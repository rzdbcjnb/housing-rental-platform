package com.bulongyu.housing.entity;

/**
 * 检索约束
 */
public record SearchConstraint(Field field, Operator operator, Object value, Strength strength) {
    /**
     * 检索约束
     */
    public enum Field { PRICE, BEDROOMS, LIVING_ROOMS, BATHROOMS, KITCHENS, REGION }
    /**
     * 检索约束
     */
    public enum Operator { EQ, GTE, LTE, AROUND, CONTAINS }
    /**
     * 检索约束
     */
    public enum Strength { HARD, SOFT }
}
