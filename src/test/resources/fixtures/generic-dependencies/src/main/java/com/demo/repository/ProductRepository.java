package com.demo.repository;

import com.demo.entity.Product;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository extends BaseRepository<Product, Long> {
}
