package com.bulongyu.housing.service;


import com.bulongyu.housing.entity.HouseRow;
import com.bulongyu.housing.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

/**
 * 站内通知业务服务
 */
@Service
public class HouseNotificationService {
    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;

    /**
     * 初始化 {@code HouseNotificationService} 并注入所需依赖。
     *
     * @param notificationMapper 通知数据访问组件
     * @param notificationService 通知业务服务
     */
    public HouseNotificationService(NotificationMapper notificationMapper,
                                    NotificationService notificationService) {
        this.notificationMapper = notificationMapper;
        this.notificationService = notificationService;
    }

    /**
     * 房源发布后通知后台管理员审核。
     *
     * @param house 候选房源
     */
    public void newHouse(HouseRow house) {
        for (Long adminId : notificationMapper.findAdminProfileIds()) {
            notificationService.send(adminId, house.landlordId(), "new_house",
                    "\u65b0\u623f\u6e90\u5f85\u5ba1\u6838",
                    "\u623f\u4e1c\u201c" + house.landlordUsername() + "\u201d\u53d1\u5e03\u4e86\u65b0\u623f\u6e90\u201c"
                            + house.title() + "\u201d\uff0c\u8bf7\u5ba1\u6838",
                    house.id());
        }
    }

    /**
     * 审核房源并同步更新发布状态。
     *
     * @param house 候选房源
     * @param adminProfileId 管理员用户资料编号
     * @param status 状态
     */
    public void audit(HouseRow house, Long adminProfileId, String status) {
        String statusText = "approved".equals(status) ? "\u5df2\u901a\u8fc7" : "\u5df2\u62d2\u7edd";
        notificationService.send(house.landlordId(), adminProfileId, "audit",
                "\u623f\u6e90\u5ba1\u6838" + statusText,
                "\u60a8\u7684\u623f\u6e90\u201c" + house.title() + "\u201d" + statusText + "\u5ba1\u6838",
                house.id());
    }

    /**
     * 房源下架后通知相关用户。
     *
     * @param house 候选房源
     * @param adminProfileId 管理员用户资料编号
     */
    public void offline(HouseRow house, Long adminProfileId) {
        notificationService.send(house.landlordId(), adminProfileId, "status",
                "\u623f\u6e90\u72b6\u6001\u53d8\u66f4",
                "\u60a8\u7684\u623f\u6e90\u201c" + house.title() + "\u201d\u72b6\u6001\u53d8\u66f4\u4e3a\uff1a\u5df2\u4e0b\u67b6",
                house.id());
    }
}
