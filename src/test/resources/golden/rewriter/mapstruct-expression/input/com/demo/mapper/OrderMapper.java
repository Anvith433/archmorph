package com.demo.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Totals are formatted by {@link com.demo.util.Totals}. */
@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "total", expression = "java(com.demo.util.Totals.format(order.getTotal()))")
    @Mapping(target = "label", constant = "com.demo.util.Totals")
    @Mapping(target = "currency", defaultExpression = "java( com.demo.util.Totals.DEFAULT_CURRENCY )")
    Object toDto(Object order);
}
