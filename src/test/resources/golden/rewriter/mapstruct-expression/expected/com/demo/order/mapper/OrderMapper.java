package com.demo.order.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Totals are formatted by {@link com.demo.order.Totals}. */
@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "total", expression = "java(com.demo.order.Totals.format(order.getTotal()))")
    @Mapping(target = "label", constant = "com.demo.util.Totals")
    @Mapping(target = "currency", defaultExpression = "java( com.demo.order.Totals.DEFAULT_CURRENCY )")
    Object toDto(Object order);
}
