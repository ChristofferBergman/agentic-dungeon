package org.bergman.agenticDungeon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.SessionConfig;
import org.springframework.stereotype.Component;

/* Initial setup on empty db:
CREATE (r:Room:Original:Start {name: "Porch", description: "You find yourself standing on the creaking wooden porch of an enormous, ancient castle, its weathered stone walls looming ominously in the misty twilight. Before you rises a towering wooden door, dark and heavy with age, its iron hinges rusted and ornate carvings half-faded by time, as though guarding secrets long buried within the castle's shadowed halls.", position: Point({x: 0, y: 0})})
CREATE (r2:Room:Original {name: "", description: "", position: Point({x: 0, y: 1})})
CREATE (r)-[:DOOR {direction: "N"}]->(r2)
CREATE (r2)-[:DOOR {direction: "S"}]->(r)
 */

@Component
public class Neo4jConnection {
	private static final String DB_NAME = System.getenv("DB_NAME");

	private final Driver driver;

	public Neo4jConnection(Driver driver) {
		this.driver = driver;
	}

	public String createPlayer(String name) {
		var query = new Query(
				"""
				CREATE (p:Player {name: $name})
				MATCH (r:Start)
				CREATE (p)-[:IN]->(r)
				RETURN elementId(p) AS player
				""",
				Map.of("name", name));

		try (var session = driver.session(SessionConfig.forDatabase(DB_NAME))) {
			var record = session.executeWrite(tx -> tx.run(query).single());
			return record.get("player").asString();
		}
	}

	public Map<String, Object> getCurrentRoom(String player) {
		var query = new Query(
				"""
				MATCH (player:Player) WHERE elementId(player) = $player
				MATCH (player)-[:IN]->(room:Room)
				WITH room, room.position AS pos
				WITH room,
				  (CASE WHEN room.name IS NOT NULL AND room.name <> "" THEN true
				        WHEN room.description IS NOT NULL AND room.description <> "" THEN true
				        ELSE false END) AS hasContent,
				  EXISTS{(room)-[:DOOR {direction: "N"}]->(:Room)} AS hasDoorN,
				  EXISTS{(room)-[:DOOR {direction: "S"}]->(:Room)} AS hasDoorS,
				  EXISTS{(room)-[:DOOR {direction: "W"}]->(:Room)} AS hasDoorW,
				  EXISTS{(room)-[:DOOR {direction: "E"}]->(:Room)} AS hasDoorE,
				  EXISTS{(r:Room {position: point({x: pos.x, y: pos.y + 1})}) WHERE r.name <> ""} AS existsN,
				  EXISTS{(r:Room {position: point({x: pos.x, y: pos.y - 1})}) WHERE r.name <> ""} AS existsS,
				  EXISTS{(r:Room {position: point({x: pos.x - 1, y: pos.y})}) WHERE r.name <> ""} AS existsW,
				  EXISTS{(r:Room {position: point({x: pos.x + 1, y: pos.y})}) WHERE r.name <> ""} AS existsE
				
				RETURN room
				{
				  .name,
				  .description,
				  id: elementId(room),
				  N: CASE WHEN hasDoorN THEN "Door"
				          WHEN hasContent OR existsN THEN "No door"
				          ELSE "Unknown" END,
				  S: CASE WHEN hasDoorS THEN "Door"
				          WHEN hasContent OR existsS THEN "No door"
				          ELSE "Unknown" END,
				  W: CASE WHEN hasDoorW THEN "Door"
				          WHEN hasContent OR existsW THEN "No door"
				          ELSE "Unknown" END,
				  E: CASE WHEN hasDoorE THEN "Door"
				          WHEN hasContent OR existsE THEN "No door"
				          ELSE "Unknown" END
				}
				""",
				Map.of("player", player));

		try (var session = driver.session(SessionConfig.forDatabase(DB_NAME))) {
			var record = session.executeRead(tx -> tx.run(query).single());
			return record.get("room").asMap();
		}
	}

	public List<Map<String, Object>> getGhosts(String player) {
		var query = new Query(
				"""
				MATCH (player:Player) WHERE elementId(player) = $player
				MATCH (player)-[:IN]->(room:Room)
				MATCH (room)<-[:IN]-(ghost:Ghost)
				OPTIONAL MATCH (ghost)-[t:THINKS_OF]->(player)
				RETURN ghost
				{
					.name,
					.appearance,
					.character,
					id: elementId(ghost),
					opinionOnPlayer: CASE WHEN t IS NULL THEN "Nothing"
										  ELSE t.opinion END
				}
				""",
				Map.of("player", player));

		try (var session = driver.session(SessionConfig.forDatabase(DB_NAME))) {
			var record = session.executeRead(tx -> tx.run(query).list());
			return record.stream().map(r -> r.get("ghost").asMap()).toList();
		}
	}

	public void createGhost(String player, String name, String appearance, String character) {
		var query = new Query(
				"""
				MATCH (player:Player) WHERE elementId(player) = $player
				MATCH (player)-[:IN]->(room:Room)
				CREATE (g:Ghost {name: $name, appearance: $appearance, character: $character})
				CREATE (g)-[:IN]->(room)
				""",
				Map.of("player", player, "name", name, "appearance", appearance, "character", character));

		try (var session = driver.session(SessionConfig.forDatabase(DB_NAME))) {
			session.executeWriteWithoutResult(tx -> tx.run(query).consume());
		}
	}

	public String updateGhostPlayerOpinion(String player, String ghostId, String opinion) {
		var query = new Query(
				"""
				MATCH (player:Player) WHERE elementId(player) = $player
				MATCH (ghost:Ghost) WHERE elementId(ghost) = $ghostId
				MERGE (ghost)-[t:THINKS_OF]->(player)
				SET t.opinion = $opinion
				""",
				Map.of("player", player, "ghostId", ghostId, "opinion", opinion));

		try (var session = driver.session(SessionConfig.forDatabase(DB_NAME))) {
			session.executeWriteWithoutResult(tx -> tx.run(query).consume());
			return opinion;
		}
	}

	public void createRoom(String player, String name, String description, String newDoors) {
		var room = getCurrentRoom(player);
		ArrayList<String> doors = new ArrayList<>(newDoors.chars().mapToObj(c -> String.valueOf((char) c)).toList());
		Iterator<String> it = doors.iterator();
		while (it.hasNext()) {
			String d = it.next();
			if (!room.containsKey(d) || !room.get(d).toString().equalsIgnoreCase("Unknown")) {
				it.remove();
			}
		}

		var query = new Query(
				"""
				MATCH (player:Player) WHERE elementId(player) = $player
				MATCH (player)-[:IN]->(room:Room)
				SET room.name = $name, room.description = $description
				UNWIND $doors AS door
				WITH room, door,
					 CASE
					    WHEN door = "N" THEN Point({x: room.position.x, y: room.position.y + 1})
					    WHEN door = "S" THEN Point({x: room.position.x, y: room.position.y - 1})
					    WHEN door = "W" THEN Point({x: room.position.x - 1, y: room.position.y})
					    ELSE Point({x: room.position.x + 1, y: room.position.y}) END AS newPos,
					 CASE
					    WHEN door = "N" THEN "S"
					    WHEN door = "S" THEN "N"
					    WHEN door = "W" THEN "E"
					    ELSE "W" END AS revDir
				MERGE (newRoom:Room {position: newPos})
				ON CREATE SET newRoom.name = "", newRoom.description = ""
				MERGE (room)-[:DOOR {direction: door}]->(newRoom)
				MERGE (newRoom)-[:DOOR {direction: revDir}]->(room)
				""",
				Map.of("player", player, "name", name, "description", description, "doors", doors));

		try (var session = driver.session(SessionConfig.forDatabase(DB_NAME))) {
			session.executeWriteWithoutResult(tx -> tx.run(query).consume());
		}
	}

	public void move(String player, String direction) {
		var query = new Query(
				"""
				MATCH (player:Player) WHERE elementId(player) = $player
				MATCH (player)-[i:IN]->(room:Room)
				MATCH (room)-[:DOOR {direction: $direction}]->(newRoom:Room)
				DELETE i
				CREATE (player)-[:IN]->(newRoom)
				""",
				Map.of("player", player, "direction", direction));

		try (var session = driver.session(SessionConfig.forDatabase(DB_NAME))) {
			session.executeWriteWithoutResult(tx -> tx.run(query).consume());
		}
	}

	public void killPlayer(String player) {
		var query = new Query(
				"""
				MATCH (player:Player) WHERE elementId(player) = $player
				DETACH DELETE player
				""",
				Map.of("player", player));

		try (var session = driver.session(SessionConfig.forDatabase(DB_NAME))) {
			session.executeWriteWithoutResult(tx -> tx.run(query).consume());
		}
	}
}
