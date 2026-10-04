    package com.demo.service;

    import com.demo.common.DateUtils;
import com.demo.common.PageResult;
    import com.demo.entity.Order;
    import java.util.List;
    import org.springframework.stereotype.Service;

    @Service
    public class OrderService {

        public PageResult<Order> page() {
            PageResult<Order> result = new PageResult<>();
            result.items = List.of();
            return result;
        }

        public java.time.LocalDate day() {
            return DateUtils.today();
        }
    }
