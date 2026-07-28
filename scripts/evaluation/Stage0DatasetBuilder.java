import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the versioned stage-0 retrieval snapshot and the initial relevance judgments.
 *
 * <p>The builder reads only public houses and never mutates the source database. Database
 * credentials are read from DB_URL, DB_USERNAME and DB_PASSWORD environment variables.</p>
 */
public final class Stage0DatasetBuilder {
    private static final String DATASET_VERSION = "stage0-v1";
    private static final int EXPECTED_HOUSE_COUNT = 110;
    private static final int EXPECTED_QUERY_COUNT = 150;

    private static final String HOUSE_SQL = """
            SELECT h.id,h.title,h.description,h.price,h.area,h.rooms,h.bedroom_count,
                   h.living_room_count,h.bathroom_count,h.kitchen_count,h.house_type,
                   h.address_detail,h.status,h.is_active,
                   r.name region_name,r.level region_level,
                   p.name parent_name,p.level parent_level,
                   g.name grand_name,g.level grand_level
            FROM house h
            LEFT JOIN area r ON r.id=h.region_id
            LEFT JOIN area p ON p.id=r.parent_id
            LEFT JOIN area g ON g.id=p.parent_id
            WHERE h.status='approved' AND h.is_active=TRUE
            ORDER BY h.id
            """;

    private Stage0DatasetBuilder() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Stage0DatasetBuilder <output-directory>");
        }
        Path outputDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(outputDirectory);

        List<House> houses = loadHouses();
        if (houses.size() != EXPECTED_HOUSE_COUNT) {
            throw new IllegalStateException(
                    "Expected " + EXPECTED_HOUSE_COUNT + " public houses but found " + houses.size());
        }

        List<EvaluationQuery> queries = new ArrayList<>();
        queries.addAll(structuredQueries(houses));
        queries.addAll(semanticQueries(houses));
        queries.addAll(mixedQueries(houses));
        queries.addAll(zeroHitQueries(houses));
        queries.addAll(conflictAndDirtyQueries(houses));
        validateDataset(houses, queries);

        Path snapshotPath = outputDirectory.resolve("houses.jsonl");
        Path queriesPath = outputDirectory.resolve("queries.jsonl");
        writeJsonLines(snapshotPath, houses.stream().map(Stage0DatasetBuilder::houseJson).toList());
        writeJsonLines(queriesPath, queries.stream().map(Stage0DatasetBuilder::queryJson).toList());

        Map<String, Object> manifest = manifest(houses, queries, snapshotPath, queriesPath);
        Files.writeString(
                outputDirectory.resolve("manifest.json"),
                json(manifest) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        System.out.printf(
                Locale.ROOT,
                "Built %s: houses=%d queries=%d incompleteRegions=%d%n",
                DATASET_VERSION,
                houses.size(),
                queries.size(),
                houses.stream().filter(House::regionIncomplete).count());
    }

    private static List<House> loadHouses() throws SQLException {
        String url = requiredEnvironment("DB_URL");
        String username = requiredEnvironment("DB_USERNAME");
        String password = requiredEnvironment("DB_PASSWORD");
        List<House> houses = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = connection.prepareStatement(HOUSE_SQL);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Region region = normalizeRegion(
                        result.getString("region_name"),
                        result.getString("region_level"),
                        result.getString("parent_name"),
                        result.getString("parent_level"),
                        result.getString("grand_name"),
                        result.getString("grand_level"));
                houses.add(new House(
                        result.getLong("id"),
                        nullToEmpty(result.getString("title")),
                        nullToEmpty(result.getString("description")),
                        result.getBigDecimal("price"),
                        result.getInt("area"),
                        nullToEmpty(result.getString("rooms")),
                        result.getInt("bedroom_count"),
                        result.getInt("living_room_count"),
                        nullableInteger(result, "bathroom_count"),
                        nullableInteger(result, "kitchen_count"),
                        nullToEmpty(result.getString("house_type")),
                        region.city(),
                        region.district(),
                        region.street(),
                        nullToEmpty(result.getString("address_detail")),
                        nullToEmpty(result.getString("status")),
                        result.getBoolean("is_active"),
                        region.qualityFlags()));
            }
        }
        return List.copyOf(houses);
    }

    private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static Region normalizeRegion(String regionName,
                                          String regionLevel,
                                          String parentName,
                                          String parentLevel,
                                          String grandName,
                                          String grandLevel) {
        String level = nullToEmpty(regionLevel).toLowerCase(Locale.ROOT);
        List<String> flags = new ArrayList<>();
        String city = "";
        String district = "";
        String street = "";
        if ("street".equals(level)) {
            street = nullToEmpty(regionName);
            district = "district".equalsIgnoreCase(parentLevel) ? nullToEmpty(parentName) : "";
            city = "province".equalsIgnoreCase(grandLevel) ? nullToEmpty(grandName) : "";
        }
        else if ("district".equals(level)) {
            district = nullToEmpty(regionName);
            city = "province".equalsIgnoreCase(parentLevel) ? nullToEmpty(parentName) : "";
            flags.add("REGION_NOT_STREET");
        }
        else if ("province".equals(level)) {
            city = nullToEmpty(regionName);
            flags.add("REGION_NOT_STREET");
        }
        else {
            flags.add("REGION_LEVEL_INVALID");
        }
        if (city.isBlank() || district.isBlank() || street.isBlank()) {
            flags.add("REGION_HIERARCHY_INCOMPLETE");
        }
        return new Region(city, district, street, List.copyOf(new LinkedHashSet<>(flags)));
    }

    private static List<EvaluationQuery> structuredQueries(List<House> houses) {
        List<EvaluationQuery> queries = new ArrayList<>();
        for (int index = 0; index < 45; index++) {
            House seed = houses.get((index * 17) % houses.size());
            String region = bestRegion(seed, index);
            BigDecimal upperPrice = roundUp(seed.price(), 500);
            BigDecimal lowerPrice = seed.price().subtract(new BigDecimal("750")).max(BigDecimal.ONE);
            List<Constraint> constraints = new ArrayList<>();
            String text;
            switch (index % 5) {
                case 0 -> {
                    constraints.add(hard("REGION", "CONTAINS", region));
                    constraints.add(hard("PRICE", "LTE", upperPrice));
                    text = "想在%s租房，月租不超过%s元".formatted(region, plain(upperPrice));
                }
                case 1 -> {
                    constraints.add(hard("REGION", "CONTAINS", region));
                    constraints.add(hard("BEDROOMS", "EQ", seed.bedrooms()));
                    text = "找%s的%d室房源".formatted(region, seed.bedrooms());
                }
                case 2 -> {
                    constraints.add(hard("REGION", "CONTAINS", region));
                    constraints.add(hard("PRICE", "LTE", upperPrice));
                    constraints.add(hard("BEDROOMS", "EQ", seed.bedrooms()));
                    text = "%s有没有月租%s元以内的%d室".formatted(
                            region, plain(upperPrice), seed.bedrooms());
                }
                case 3 -> {
                    constraints.add(hard("PRICE", "GTE", lowerPrice));
                    constraints.add(hard("PRICE", "LTE", upperPrice));
                    constraints.add(hard("BEDROOMS", "GTE", seed.bedrooms()));
                    text = "预算%s到%s元，至少%d室".formatted(
                            plain(lowerPrice), plain(upperPrice), seed.bedrooms());
                }
                default -> {
                    constraints.add(hard("REGION", "CONTAINS", region));
                    constraints.add(hard("BEDROOMS", "GTE", seed.bedrooms()));
                    StringBuilder requirement = new StringBuilder("%s至少%d室".formatted(
                            region, seed.bedrooms()));
                    if (seed.bathrooms() != null) {
                        constraints.add(hard("BATHROOMS", "GTE", seed.bathrooms()));
                        requirement.append("、").append(seed.bathrooms()).append("卫");
                    }
                    if (seed.kitchens() != null) {
                        constraints.add(hard("KITCHENS", "GTE", seed.kitchens()));
                        requirement.append("、").append(seed.kitchens()).append("厨");
                    }
                    text = requirement.append("的房源").toString();
                }
            }
            List<Judgment> judgments = judgmentsForHardConstraints(houses, constraints);
            requireResults("STR-%03d".formatted(index + 1), judgments);
            queries.add(new EvaluationQuery(
                    "STR-%03d".formatted(index + 1),
                    "STRUCTURED",
                    text,
                    true,
                    "RESULTS",
                    List.copyOf(constraints),
                    judgments,
                    "AUTO_DERIVED",
                    List.of(),
                    "All listed houses satisfy every hard constraint; unlisted houses have relevance 0."));
        }
        return queries;
    }

    private static List<EvaluationQuery> semanticQueries(List<House> houses) {
        List<EvaluationQuery> queries = new ArrayList<>();
        int queryNumber = 1;
        for (Feature feature : Feature.values()) {
            for (String wording : feature.wordings) {
                List<Judgment> judgments = semanticJudgments(houses, feature, List.of());
                requireResults("SEM-%03d".formatted(queryNumber), judgments);
                queries.add(new EvaluationQuery(
                        "SEM-%03d".formatted(queryNumber++),
                        "SEMANTIC",
                        wording,
                        true,
                        "RESULTS",
                        List.of(),
                        judgments,
                        "HUMAN_CONFIRMED",
                        List.of("SEMANTIC_RELEVANCE_CONFIRMED_V1_F2_L2"),
                        "Relevance 2 uses explicit feature evidence; relevance 1 uses weaker related evidence."));
            }
        }
        return queries;
    }

    private static List<EvaluationQuery> mixedQueries(List<House> houses) {
        List<EvaluationQuery> queries = new ArrayList<>();
        int queryNumber = 1;
        for (Feature feature : Feature.values()) {
            List<House> strongMatches = houses.stream()
                    .filter(house -> semanticGrade(feature, house) == 2)
                    .toList();
            if (strongMatches.isEmpty()) {
                throw new IllegalStateException("No strong match for feature " + feature.code);
            }
            for (int variant = 0; variant < feature.wordings.size(); variant++) {
                House seed = strongMatches.get(variant % strongMatches.size());
                String region = bestRegion(seed, variant + feature.ordinal());
                BigDecimal upperPrice = roundUp(seed.price(), 500);
                BigDecimal lowerPrice = seed.price().subtract(new BigDecimal("1000")).max(BigDecimal.ONE);
                List<Constraint> constraints = new ArrayList<>();
                String suffix;
                switch (variant) {
                    case 0 -> {
                        constraints.add(hard("REGION", "CONTAINS", region));
                        constraints.add(hard("PRICE", "LTE", upperPrice));
                        suffix = "，位置在%s，月租不超过%s元".formatted(region, plain(upperPrice));
                    }
                    case 1 -> {
                        constraints.add(hard("REGION", "CONTAINS", region));
                        constraints.add(hard("BEDROOMS", "EQ", seed.bedrooms()));
                        suffix = "，要%s的%d室".formatted(region, seed.bedrooms());
                    }
                    case 2 -> {
                        constraints.add(hard("PRICE", "GTE", lowerPrice));
                        constraints.add(hard("PRICE", "LTE", upperPrice));
                        suffix = "，预算%s到%s元".formatted(plain(lowerPrice), plain(upperPrice));
                    }
                    default -> {
                        constraints.add(hard("PRICE", "LTE", upperPrice));
                        constraints.add(hard("BEDROOMS", "GTE", seed.bedrooms()));
                        suffix = "，月租不超过%s元并且至少%d室".formatted(
                                plain(upperPrice), seed.bedrooms());
                    }
                }
                List<Judgment> judgments = semanticJudgments(houses, feature, constraints);
                requireResults("MIX-%03d".formatted(queryNumber), judgments);
                queries.add(new EvaluationQuery(
                        "MIX-%03d".formatted(queryNumber++),
                        "MIXED",
                        feature.wordings.get(variant) + suffix,
                        true,
                        "RESULTS",
                        List.copyOf(constraints),
                        judgments,
                        "HUMAN_CONFIRMED",
                        List.of("SEMANTIC_RELEVANCE_CONFIRMED_V1_F2_L2"),
                        "Hard constraints are auto-derived; semantic relevance grades require human confirmation."));
            }
        }
        return queries;
    }

    private static List<EvaluationQuery> zeroHitQueries(List<House> houses) {
        List<EvaluationQuery> queries = new ArrayList<>();
        List<String> cities = houses.stream()
                .map(House::city)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        for (int index = 0; index < 5; index++) {
            String city = cities.get(index % cities.size());
            List<Constraint> constraints = List.of(
                    hard("REGION", "CONTAINS", city),
                    hard("PRICE", "LTE", new BigDecimal("50")));
            queries.add(emptyQuery(
                    "ZERO-%03d".formatted(index + 1),
                    "%s月租不超过50元的房源".formatted(city),
                    constraints,
                    "AUTO_DERIVED",
                    List.of()));
        }
        for (int index = 5; index < 10; index++) {
            String city = cities.get(index % cities.size());
            List<Constraint> constraints = List.of(
                    hard("REGION", "CONTAINS", city),
                    hard("BEDROOMS", "GTE", 10));
            queries.add(emptyQuery(
                    "ZERO-%03d".formatted(index + 1),
                    "%s至少10室的出租房".formatted(city),
                    constraints,
                    "AUTO_DERIVED",
                    List.of()));
        }

        List<String> absentFeatures = List.of(
                "带私人直升机停机坪的出租房",
                "房子里要有室内标准马术训练场",
                "配备专业录音棚和隔音控制室的住宅",
                "带恒温奥运标准泳池的普通出租房",
                "房屋自带私人天文台和大型望远镜",
                "客厅里有保龄球道的出租房",
                "带私人飞机跑道的市中心住宅",
                "房屋内部有大型冰球场",
                "自带潜水训练池和减压舱的出租房",
                "配备大型电影摄影棚的住宅");
        for (int index = 0; index < absentFeatures.size(); index++) {
            queries.add(emptyQuery(
                    "ZERO-%03d".formatted(index + 11),
                    absentFeatures.get(index),
                    List.of(),
                    "HUMAN_CONFIRMED",
                    List.of("SEMANTIC_ZERO_HIT_CONFIRMED_Z1")));
        }
        return queries;
    }

    private static List<EvaluationQuery> conflictAndDirtyQueries(List<House> houses) {
        List<EvaluationQuery> queries = new ArrayList<>();
        queries.add(invalidQuery(
                "EDGE-001",
                "最低预算5000元但最高预算3000元",
                List.of(hard("PRICE", "GTE", new BigDecimal("5000")),
                        hard("PRICE", "LTE", new BigDecimal("3000"))),
                "Minimum price is greater than maximum price."));
        queries.add(invalidQuery(
                "EDGE-002",
                "必须正好1室，同时至少要3室",
                List.of(hard("BEDROOMS", "EQ", 1), hard("BEDROOMS", "GTE", 3)),
                "Exact and minimum bedroom constraints conflict."));
        queries.add(invalidQuery(
                "EDGE-003",
                "找月租不超过0元的房源",
                List.of(hard("PRICE", "LTE", BigDecimal.ZERO)),
                "Non-positive price is invalid input."));
        queries.add(invalidQuery(
                "EDGE-004",
                "找至少负一室的房源",
                List.of(hard("BEDROOMS", "GTE", -1)),
                "Negative room count is invalid input."));

        List<Judgment> completeDalian = houses.stream()
                .filter(house -> containsIgnoreCase(house.fullRegion(), "大连"))
                .filter(house -> !house.regionIncomplete())
                .map(house -> new Judgment(house.id(), 2, "位于大连且城市、区县、街道层级完整"))
                .toList();
        queries.add(new EvaluationQuery(
                "EDGE-005",
                "CONFLICT_OR_DIRTY",
                "大连市且区县、街道信息完整的房源",
                false,
                "UNSUPPORTED",
                List.of(hard("REGION", "CONTAINS", "大连")),
                completeDalian,
                "AUTO_DERIVED",
                List.of("REQUIRES_REGION_COMPLETENESS_FILTER"),
                "The current search contract cannot express a region-completeness constraint."));
        return queries;
    }

    private static EvaluationQuery emptyQuery(String id,
                                              String text,
                                              List<Constraint> constraints,
                                              String labelStatus,
                                              List<String> flags) {
        return new EvaluationQuery(
                id,
                "ZERO_HIT",
                text,
                true,
                "EMPTY",
                constraints,
                List.of(),
                labelStatus,
                flags,
                "No public house is relevant; any returned house is a false positive.");
    }

    private static EvaluationQuery invalidQuery(String id,
                                                String text,
                                                List<Constraint> constraints,
                                                String notes) {
        return new EvaluationQuery(
                id,
                "CONFLICT_OR_DIRTY",
                text,
                true,
                "INVALID_QUERY",
                constraints,
                List.of(),
                "AUTO_DERIVED",
                List.of("CONSTRAINT_CONFLICT_OR_INVALID_VALUE"),
                notes);
    }

    private static List<Judgment> judgmentsForHardConstraints(List<House> houses,
                                                               List<Constraint> constraints) {
        return houses.stream()
                .filter(house -> matchesAll(house, constraints))
                .map(house -> new Judgment(house.id(), 2, "满足全部硬条件"))
                .toList();
    }

    private static List<Judgment> semanticJudgments(List<House> houses,
                                                    Feature feature,
                                                    List<Constraint> constraints) {
        List<Judgment> judgments = new ArrayList<>();
        for (House house : houses) {
            if (!matchesAll(house, constraints)) {
                continue;
            }
            int grade = semanticGrade(feature, house);
            if (grade > 0) {
                judgments.add(new Judgment(
                        house.id(),
                        grade,
                        grade == 2
                                ? "文本包含“" + feature.code + "”的明确证据"
                                : "文本或户型包含“" + feature.code + "”的弱相关证据"));
            }
        }
        judgments.sort(Comparator.comparingInt(Judgment::relevance).reversed()
                .thenComparingLong(Judgment::houseId));
        return List.copyOf(judgments);
    }

    private static int semanticGrade(Feature feature, House house) {
        String text = house.semanticText();
        if (containsAny(text, feature.negativeTokens)) {
            return 0;
        }
        if (containsAny(text, feature.strongTokens)) {
            return 2;
        }
        if (containsAny(text, feature.weakTokens)) {
            return 1;
        }
        if (feature == Feature.FAMILY && house.bedrooms() >= 3) {
            return 1;
        }
        return 0;
    }

    private static boolean matchesAll(House house, List<Constraint> constraints) {
        for (Constraint constraint : constraints) {
            if (!matches(house, constraint)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(House house, Constraint constraint) {
        return switch (constraint.field()) {
            case "REGION" -> containsIgnoreCase(house.fullRegion(), constraint.value().toString());
            case "PRICE" -> compare(house.price(), (BigDecimal) constraint.value(), constraint.operator());
            case "BEDROOMS" -> compare(house.bedrooms(), ((Number) constraint.value()).intValue(),
                    constraint.operator());
            case "LIVING_ROOMS" -> compare(house.livingRooms(),
                    ((Number) constraint.value()).intValue(), constraint.operator());
            case "BATHROOMS" -> house.bathrooms() != null && compare(house.bathrooms(),
                    ((Number) constraint.value()).intValue(), constraint.operator());
            case "KITCHENS" -> house.kitchens() != null && compare(house.kitchens(),
                    ((Number) constraint.value()).intValue(), constraint.operator());
            default -> throw new IllegalArgumentException("Unknown field: " + constraint.field());
        };
    }

    private static boolean compare(BigDecimal actual, BigDecimal expected, String operator) {
        int comparison = actual.compareTo(expected);
        return switch (operator) {
            case "EQ" -> comparison == 0;
            case "GTE" -> comparison >= 0;
            case "LTE" -> comparison <= 0;
            default -> throw new IllegalArgumentException("Unsupported decimal operator: " + operator);
        };
    }

    private static boolean compare(int actual, int expected, String operator) {
        return switch (operator) {
            case "EQ" -> actual == expected;
            case "GTE" -> actual >= expected;
            case "LTE" -> actual <= expected;
            default -> throw new IllegalArgumentException("Unsupported integer operator: " + operator);
        };
    }

    private static Constraint hard(String field, String operator, Object value) {
        return new Constraint(field, operator, value, "HARD");
    }

    private static String bestRegion(House house, int variant) {
        if (variant % 3 == 0 && !house.street().isBlank()) {
            return house.street();
        }
        if (variant % 3 != 2 && !house.district().isBlank()) {
            return house.district();
        }
        return house.city();
    }

    private static BigDecimal roundUp(BigDecimal value, int unit) {
        BigDecimal divisor = BigDecimal.valueOf(unit);
        return value.divideToIntegralValue(divisor)
                .add(value.remainder(divisor).signum() == 0 ? BigDecimal.ZERO : BigDecimal.ONE)
                .multiply(divisor);
    }

    private static void validateDataset(List<House> houses, List<EvaluationQuery> queries) {
        if (queries.size() != EXPECTED_QUERY_COUNT) {
            throw new IllegalStateException(
                    "Expected " + EXPECTED_QUERY_COUNT + " queries but found " + queries.size());
        }
        Map<String, Long> expectedCategories = Map.of(
                "STRUCTURED", 45L,
                "SEMANTIC", 40L,
                "MIXED", 40L,
                "ZERO_HIT", 20L,
                "CONFLICT_OR_DIRTY", 5L);
        for (Map.Entry<String, Long> entry : expectedCategories.entrySet()) {
            long actual = queries.stream().filter(query -> entry.getKey().equals(query.category())).count();
            if (actual != entry.getValue()) {
                throw new IllegalStateException(
                        "Category " + entry.getKey() + " expected " + entry.getValue() + " but found " + actual);
            }
        }
        Set<Long> houseIds = houses.stream().map(House::id).collect(java.util.stream.Collectors.toSet());
        Set<String> queryIds = new LinkedHashSet<>();
        for (EvaluationQuery query : queries) {
            if (!queryIds.add(query.id())) {
                throw new IllegalStateException("Duplicate query ID: " + query.id());
            }
            for (Judgment judgment : query.judgments()) {
                if (!houseIds.contains(judgment.houseId())) {
                    throw new IllegalStateException(
                            "Query " + query.id() + " references missing house " + judgment.houseId());
                }
                if (judgment.relevance() < 1 || judgment.relevance() > 2) {
                    throw new IllegalStateException("Invalid relevance grade in " + query.id());
                }
            }
        }
        long incompleteRegions = houses.stream().filter(House::regionIncomplete).count();
        if (incompleteRegions != 20) {
            throw new IllegalStateException(
                    "Expected 20 incomplete regions but found " + incompleteRegions);
        }
    }

    private static void requireResults(String queryId, List<Judgment> judgments) {
        if (judgments.isEmpty()) {
            throw new IllegalStateException(queryId + " has no relevant houses");
        }
    }

    private static void writeJsonLines(Path path, List<Map<String, Object>> values) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Map<String, Object> value : values) {
                writer.write(json(value));
                writer.newLine();
            }
        }
    }

    private static Map<String, Object> houseJson(House house) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("dataset_version", DATASET_VERSION);
        value.put("house_id", house.id());
        value.put("title", house.title());
        value.put("description", house.description());
        value.put("price", house.price());
        value.put("area", house.area());
        value.put("rooms", house.rooms());
        value.put("bedroom_count", house.bedrooms());
        value.put("living_room_count", house.livingRooms());
        value.put("bathroom_count", house.bathrooms());
        value.put("kitchen_count", house.kitchens());
        value.put("house_type", house.houseType());
        value.put("city", house.city());
        value.put("district", house.district());
        value.put("street", house.street());
        value.put("address_detail", house.addressDetail());
        value.put("status", house.status());
        value.put("is_active", house.active());
        value.put("data_quality_flags", house.qualityFlags());
        return value;
    }

    private static Map<String, Object> queryJson(EvaluationQuery query) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("dataset_version", DATASET_VERSION);
        value.put("query_id", query.id());
        value.put("category", query.category());
        value.put("query", query.text());
        value.put("supported", query.supported());
        value.put("expected_outcome", query.expectedOutcome());
        value.put("constraints", query.constraints().stream().map(Stage0DatasetBuilder::constraintJson).toList());
        value.put("judgments", query.judgments().stream().map(Stage0DatasetBuilder::judgmentJson).toList());
        value.put("label_status", query.labelStatus());
        value.put("review_flags", query.reviewFlags());
        value.put("notes", query.notes());
        return value;
    }

    private static Map<String, Object> constraintJson(Constraint constraint) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("field", constraint.field());
        value.put("operator", constraint.operator());
        value.put("value", constraint.value());
        value.put("strength", constraint.strength());
        return value;
    }

    private static Map<String, Object> judgmentJson(Judgment judgment) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("house_id", judgment.houseId());
        value.put("relevance", judgment.relevance());
        value.put("reason", judgment.reason());
        return value;
    }

    private static Map<String, Object> manifest(List<House> houses,
                                                List<EvaluationQuery> queries,
                                                Path snapshotPath,
                                                Path queriesPath) throws IOException, NoSuchAlgorithmException {
        Map<String, Object> categories = new LinkedHashMap<>();
        for (String category : List.of(
                "STRUCTURED", "SEMANTIC", "MIXED", "ZERO_HIT", "CONFLICT_OR_DIRTY")) {
            categories.put(category, queries.stream().filter(query -> category.equals(query.category())).count());
        }
        Map<String, Object> labels = new LinkedHashMap<>();
        for (String status : List.of("AUTO_DERIVED", "HUMAN_CONFIRMED")) {
            labels.put(status, queries.stream().filter(query -> status.equals(query.labelStatus())).count());
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("dataset_version", DATASET_VERSION);
        value.put("source", "local MySQL public houses: status=approved and is_active=true");
        value.put("house_count", houses.size());
        value.put("query_count", queries.size());
        value.put("incomplete_region_count", houses.stream().filter(House::regionIncomplete).count());
        value.put("category_counts", categories);
        value.put("label_status_counts", labels);
        value.put("unlisted_judgment_grade", 0);
        value.put("houses_sha256", sha256(snapshotPath));
        value.put("queries_sha256", sha256(queriesPath));
        return value;
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        byte[] bytes = Files.readAllBytes(path);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder();
        for (byte current : digest) {
            value.append(String.format(Locale.ROOT, "%02x", current));
        }
        return value.toString();
    }

    private static String json(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return quote(string);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder result = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    result.append(',');
                }
                first = false;
                result.append(quote(entry.getKey().toString())).append(':').append(json(entry.getValue()));
            }
            return result.append('}').toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder result = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    result.append(',');
                }
                first = false;
                result.append(json(item));
            }
            return result.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        result.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    }
                    else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    private static boolean containsAny(String text, List<String> tokens) {
        for (String token : tokens) {
            if (containsIgnoreCase(text, token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(String text, String token) {
        return text.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private enum Feature {
        SUBWAY(
                "近地铁和通勤方便",
                List.of("想找离地铁近、通勤方便的房子", "优先推荐靠近地铁站的房源", "希望离地铁近一些，日常通勤方便", "不想每天走太远，想住在地铁附近"),
                List.of("地铁"),
                List.of("交通便利", "出行方便", "出行便利", "交通枢纽"),
                List.of()),
        MOVE_IN(
                "家电齐全和拎包入住",
                List.of("想找家电齐全可以拎包入住的房子", "希望家电齐全，入住后不用再添置", "不想添置家具家电，直接入住最好", "优先推荐家电齐全、可以直接入住的房源"),
                List.of("拎包入住", "家电齐全", "全新家电", "精装全配"),
                List.of("精装修", "豪华装修", "现代装修"),
                List.of()),
        QUIET(
                "安静和环境舒适",
                List.of("想住在安静、环境舒适的小区", "希望小区绿化好，居住不要太吵", "优先环境优美适合休息的房子", "想找安静、环境舒适、适合长期居住的房源"),
                List.of("安静", "环境优美", "绿化率高", "花园"),
                List.of("居住舒适", "小区环境"),
                List.of()),
        SEA_VIEW(
                "海景和开阔视野",
                List.of("想找能看到海景的房子", "希望有海景，视野开阔并能看日出日落", "优先推荐海景开阔、景观和夜景好的房源", "想住在靠海并且能看到海景的住宅"),
                List.of("海景", "日出日落"),
                List.of(),
                List.of()),
        FAMILY(
                "适合家庭居住",
                List.of("想找适合一家人长期居住的房子", "带孩子租房，希望环境适合家庭", "优先推荐适合小家庭的住宅", "希望空间适合家庭生活和聚会"),
                List.of("家庭", "有孩子", "小家庭", "家庭聚会"),
                List.of("居住舒适"),
                List.of()),
        YOUNG_RENTER(
                "适合年轻租客",
                List.of("刚工作，想找适合年轻人的房子", "年轻租客想找适合年轻人居住的房子", "希望房源明确适合年轻租客居住", "刚毕业或刚工作的年轻人想找合适住处"),
                List.of("年轻人", "单身", "情侣", "白领", "学生", "刚工作"),
                List.of("上班族", "互联网从业者", "科技从业者"),
                List.of()),
        SCHOOL(
                "教育和学区资源",
                List.of("为了孩子上学想找学区房", "希望附近教育资源丰富", "优先推荐靠近学校或名校的房源", "有孩子，租房时很看重学区条件"),
                List.of("学区", "名校", "教育资源", "学校"),
                List.of(),
                List.of()),
        PREMIUM(
                "装修品质和配置",
                List.of("想找装修品质好、配置较新的房子", "希望装修和配置品质高，优先豪华装修和品牌家电", "希望装修和配置品质高，最好新装修或首次出租", "希望装修和配置品质高，偏好现代装修和智能家居"),
                List.of("豪华装修", "品牌家电", "全新家电", "首次出租", "品质保证"),
                List.of("精装修", "现代简约", "现代装修", "智能家居"),
                List.of()),
        PET(
                "允许养宠物",
                List.of("我养宠物，需要允许养宠物的房子", "只推荐明确可以带宠物入住的房源", "有一只猫，想找宠物友好的出租房", "租房条件里最重要的是可以养宠物"),
                List.of("可养宠物"),
                List.of(),
                List.of("禁止养宠物")),
        FLEXIBLE(
                "灵活租期和低支付压力",
                List.of("希望可以短租，付款压力小一些", "希望租期灵活，同时可以月付、低押金或免押金", "想找可短租或租期灵活，并且付款压力较小的房子", "希望短租或半年内入住，并且押金和水电负担较低"),
                List.of("可短租", "月付", "无押金", "水电全免"),
                List.of("半年起租", "押一付一"),
                List.of());

        private final String code;
        private final List<String> wordings;
        private final List<String> strongTokens;
        private final List<String> weakTokens;
        private final List<String> negativeTokens;

        Feature(String code,
                List<String> wordings,
                List<String> strongTokens,
                List<String> weakTokens,
                List<String> negativeTokens) {
            this.code = code;
            this.wordings = wordings;
            this.strongTokens = strongTokens;
            this.weakTokens = weakTokens;
            this.negativeTokens = negativeTokens;
        }
    }

    private record House(long id,
                         String title,
                         String description,
                         BigDecimal price,
                         int area,
                         String rooms,
                         int bedrooms,
                         int livingRooms,
                         Integer bathrooms,
                         Integer kitchens,
                         String houseType,
                         String city,
                         String district,
                         String street,
                         String addressDetail,
                         String status,
                         boolean active,
                         List<String> qualityFlags) {
        String fullRegion() {
            return String.join("", List.of(city, district, street));
        }

        String semanticText() {
            return String.join(" ", title, description, fullRegion(), addressDetail);
        }

        boolean regionIncomplete() {
            return qualityFlags.contains("REGION_HIERARCHY_INCOMPLETE");
        }
    }

    private record Region(String city, String district, String street, List<String> qualityFlags) {
    }

    private record Constraint(String field, String operator, Object value, String strength) {
    }

    private record Judgment(long houseId, int relevance, String reason) {
    }

    private record EvaluationQuery(String id,
                                   String category,
                                   String text,
                                   boolean supported,
                                   String expectedOutcome,
                                   List<Constraint> constraints,
                                   List<Judgment> judgments,
                                   String labelStatus,
                                   List<String> reviewFlags,
                                   String notes) {
    }
}
