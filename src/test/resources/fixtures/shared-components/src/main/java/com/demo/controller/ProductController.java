package com.demo.controller;

import com.demo.common.ApiResponse;
import com.demo.entity.Product;
import com.demo.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Object> list() {
        return new ApiResponse<>(service != null ? new Product() : null);
    }
}
