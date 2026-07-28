package com.bulongyu.housing.vo;

import com.bulongyu.housing.entity.AdminHouseRow;
import com.bulongyu.housing.entity.AdminUserRow;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 后台管理用户返回数据
 */
public record AdminUserView(Long id, String username, String email, Boolean isActive,
                       LocalDateTime dateJoined, String phone, String role, String avatar,
                       LocalDateTime profileCreateTime) {
    /**
     * 后台管理用户返回数据
     *
     * @param row 数据库查询结果
     */
public static AdminUserView from(AdminUserRow row) {
        return new AdminUserView(row.id(), row.username(), row.email(), row.active(), row.dateJoined(),
                row.phone(), row.role(), row.avatar(), row.profileCreateTime());
    }
}
