package com.shop.mapper;

import com.shop.dto.OrderDto;
import com.shop.model.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "customerName", source = "customer.name")
    @Mapping(target = "total", expression = "java(com.shop.util.OrderTotals.format(order.getTotal()))")
    OrderDto toDto(Order order);
}
