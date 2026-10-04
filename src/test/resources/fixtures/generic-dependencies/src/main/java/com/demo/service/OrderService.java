package com.demo.service;

import com.demo.entity.Order;
import com.demo.entity.Product;
import com.demo.repository.ProductRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final ProductRepository productRepository;

    public OrderService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Order place(Product product) {
        productRepository.find(product.getId());
        return new Order();
    }
}
