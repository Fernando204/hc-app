package com.horsecontrol.eloquestre.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** Liga o preenchimento automático de @CreatedDate / @LastModifiedDate. */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
