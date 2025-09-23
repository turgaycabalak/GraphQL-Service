//package com.graph.graphservice.config;
//
//import jakarta.persistence.EntityManagerFactory;
//
//import com.blazebit.persistence.Criteria;
//import com.blazebit.persistence.CriteriaBuilderFactory;
//import com.blazebit.persistence.integration.view.spring.EnableEntityViews;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//@EnableEntityViews("com.graph.graphservice.views")
//public class BlazePersistenceConfig {
//
//  @Bean
//  public CriteriaBuilderFactory criteriaBuilderFactory(EntityManagerFactory emf) {
//    return Criteria.getDefault().createCriteriaBuilderFactory(emf);
//  }
//}
