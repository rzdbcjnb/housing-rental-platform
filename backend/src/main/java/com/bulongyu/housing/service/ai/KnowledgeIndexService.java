package com.bulongyu.housing.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.HouseQuery;
import com.bulongyu.housing.entity.HouseRow;
import com.bulongyu.housing.mapper.HouseMapper;
import com.bulongyu.housing.entity.UserProfile;
import com.bulongyu.housing.mapper.UserMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 客服业务服务
 */
@Service
public class KnowledgeIndexService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexService.class);
    public static final String INDEX_SCHEMA_VERSION = "stage0-v1";
    private static final int HOUSE_DOCUMENTS_PER_HOUSE = 4;
    private static final int FAQ_DOCUMENT_COUNT = 7;
    private static final int INDEX_STATE_DOCUMENT_COUNT = 1;

    private static final HouseQuery ALL = new HouseQuery(null, null, null, null, null, null,
            null, null, null, null, null, null, null, null);
    private final ObjectProvider<VectorStore> stores;
    private final HouseMapper houses;
    private final UserMapper users;

    /**
     * 初始化 {@code KnowledgeIndexService} 并注入所需依赖。
     *
     * @param stores 可选的向量存储提供器
     * @param houses 候选房源列表
     * @param users 用户数据访问组件
     */
    public KnowledgeIndexService(ObjectProvider<VectorStore> stores, HouseMapper houses, UserMapper users) {
        this.stores = stores;
        this.houses = houses;
        this.users = users;
    }

    /**
     * 将已审核房源与租赁 FAQ 全量同步到向量数据库。
     *
     * @param authUserId 当前登录用户编号
     */
    public IndexResult syncAll(Long authUserId) {
        requireAdmin(authUserId);
        VectorStore store = requireStore();
        LocalDateTime sourceUpdatedAt = houses.latestPublicUpdateTime();
        long total = houses.countPublic(ALL);
        // 同步开始即移除旧水位；中途失败时 readiness 会明确失败，不能继续宣称旧索引健康。
        store.delete("type == 'index_state'");
        store.delete("type == 'house'");
        int indexed = 0;
        // 2. 分批建立索引可限制内存占用，并减少对向量数据库的往返调用。
        for (int offset = 0; offset < total; offset += 100) {
            List<Document> batch = new ArrayList<>();
            for (HouseRow house : houses.findPublic(ALL, offset, 100)) {
                batch.addAll(documents(house));
            }
            if (!batch.isEmpty()) {
                store.add(batch);
            }
            indexed += batch.size();
        }
        // 3. 房源索引完成后再重建 FAQ，两个类型互不覆盖。
        store.delete("type == 'faq'");
        List<Document> faq = faqDocuments();
        store.add(faq);
        Instant syncedAt = Instant.now();
        store.add(List.of(indexStateDocument(total, sourceUpdatedAt, syncedAt)));
        log.info("完成知识索引同步，参数：houseCount={}，houseDocumentCount={}，faqCount={}，syncedAt={}",
                total, indexed, faq.size(), syncedAt);
        return new IndexResult(total, indexed, faq.size());
    }

    /**
     * 计算当前索引应有的文档总数。
     */
    public static long expectedDocumentCount(long houseCount) {
        return houseCount * HOUSE_DOCUMENTS_PER_HOUSE + FAQ_DOCUMENT_COUNT + INDEX_STATE_DOCUMENT_COUNT;
    }

    /**
     * 将数据库时间水位转换为可跨进程比较的稳定文本。
     */
    public static String watermark(LocalDateTime value) {
        return value == null ? "NONE" : value.toString();
    }

    private Document indexStateDocument(long houseCount,
                                        LocalDateTime sourceUpdatedAt,
                                        Instant syncedAt) {
        return new Document(
                "RAG index synchronization state",
                Map.of(
                        "type", "index_state",
                        "index_schema_version", INDEX_SCHEMA_VERSION,
                        "house_count", String.valueOf(houseCount),
                        "expected_document_count", String.valueOf(expectedDocumentCount(houseCount)),
                        "source_updated_at", watermark(sourceUpdatedAt),
                        "synced_at", syncedAt.toString()));
    }

    /**
     * 将知识文本切分为适合向量检索的文档片段。
     *
     * @param h 候选房源
     */
    private List<Document> documents(HouseRow h) {
        Map<String, Object> metadata = Map.of("type", "house", "house_id", h.id());
        String prefix = "房源ID:" + h.id() + " ";
        return List.of(
                new Document(prefix + "标题:" + h.title(), metadata),
                new Document(prefix + "价格:" + h.price() + "元/月 户型:" + h.rooms()
                        + " 面积:" + h.area() + "平方米", metadata),
                new Document(prefix + "地区:" + h.fullRegionName() + " 地址:" + h.addressDetail(), metadata),
                new Document(prefix + "描述:" + (h.description() == null ? "" : h.description()), metadata));
    }

    /**
     * 构建租赁常见问题向量文档。
     */
    private List<Document> faqDocuments() {
        return List.of(
                faq("payment-deposit-3", "付款方式", "什么是押一付三？",
                        "支付一个月租金作为押金，并按三个月支付一期租金；退租押金是否返还以合同和房屋损耗为准。"),
                faq("deposit-vs-earnest", "付款方式", "押金和定金有什么区别？",
                        "押金通常用于担保履约，定金具有不同法律含义；应以合同用词和具体约定为准。"),
                faq("early-termination", "退租流程", "提前退租怎么办？",
                        "先查看合同退租和违约条款，尽早书面通知房东，协商交接、费用结算和押金处理。"),
                faq("contract-check", "合同条款", "签租房合同要注意什么？",
                        "核实出租人身份和房屋权属，明确租金、押金、期限、维修责任、费用承担和退租条款。"),
                faq("viewing-safety", "看房安全", "看房时要注意什么？",
                        "核实房源和出租人信息，实地检查水电、门窗和设施，未核实前不要支付大额费用。"),
                faq("repair-duty", "房屋维护", "租期内设施损坏谁负责？",
                        "通常自然损耗由出租人处理，使用不当造成的损坏由责任方承担，最终以合同和证据为准。"),
                faq("platform-publish-house", "平台使用", "如何发布房源？",
                        "登录后进入发布房源页面，填写标题、租金、面积、户型、地区、详细地址和房源描述，上传图片后提交。房源需要管理员审核通过并上架后才会公开展示。"));
    }

    /**
     * 构建平台租赁常见问题文档。
     *
     * @param id 编号
     * @param category 知识分类
     * @param question 用户问题
     * @param answer 模型回复
     */
    private Document faq(String id, String category, String question, String answer) {
        return new Document(question + "\n" + answer, Map.of("type", "faq", "source_id", id,
                "category", category, "question", question, "answer", answer));
    }

    /**
     * 获取已配置的向量存储；未启用时返回明确的服务不可用错误。
     */
    private VectorStore requireStore() {
        try {
            VectorStore store = stores.getIfAvailable();
            if (store != null) {
                return store;
            }
        }
        catch (RuntimeException exception) {
            log.warn("创建向量索引服务失败，参数：exceptionType={}",
                    exception.getClass().getSimpleName());
        }
        throw new BusinessException(
                "VECTOR_STORE_UNAVAILABLE",
                "向量库未启用或暂时不可用，请检查 Chroma 和 EmbeddingModel 配置",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * 查询用户资料并校验管理员权限。
     *
     * @param userId 用户编号
     */
    private void requireAdmin(Long userId) {
        UserProfile profile = users.findProfileByUserId(userId);
        if (profile == null || !"admin".equals(profile.role())) {
            throw new BusinessException("ADMIN_REQUIRED", "需要管理员权限", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * AI 客服数据模型，用于封装Index处理结果相关字段
     */
    public record IndexResult(long houses, int houseDocuments, int faqDocuments) {}
}
