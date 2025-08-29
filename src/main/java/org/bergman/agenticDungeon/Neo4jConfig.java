package org.bergman.agenticDungeon;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jConfig {
	@Bean
	Driver neo4jDriver() {
		return GraphDatabase.driver(
				System.getenv("DB_URI"),
				AuthTokens.basic(
						System.getenv("DB_USER"),
						System.getenv("DB_PWD")),
				Config.defaultConfig());
	}
}
