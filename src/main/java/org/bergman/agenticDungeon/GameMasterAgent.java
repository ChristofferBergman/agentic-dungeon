package org.bergman.agenticDungeon;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Conversation;
import com.embabel.common.ai.model.LlmOptions;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class GameMasterAgent {
	private final Neo4jConnection neo4j;
	private final AiBuilder aiBuilder;

	private String killed = null;
	private String player;

	protected GameMasterAgent(Neo4jConnection neo4j, AiBuilder aiBuilder) {
		this.neo4j = neo4j;
		this.aiBuilder = aiBuilder;
	}

	public void createPlayer(String name) {
		player = neo4j.createPlayer(name);
	}

	public String getKilled() {
		return killed;
	}

	@Tool(description =
			"""
			Gets information about the room the player is currently in. If information is missing you have to call createRoom() (and optionally
			createGhost()) to fill the room.
			"""
			)
	public Map<String, Object> getCurrentRoom() {
		return neo4j.getCurrentRoom(player);
	}

	@Tool(description =
			"""
			Retrieves the ghosts in the room where the player currently is.
			"""
			)
	public List<Map<String, Object>> getGhosts() {
		return neo4j.getGhosts(player);
	}

	@Tool(description =
			"""
			Creates a new ghost in the room the player is currently in. Only do this when you have described a new room where no-one had been.
			Call this repeatedly to create multiple ghosts if you want to (and all rooms doesn't need a ghost). The return value of this
			will be the same as getGhosts() after the new ghost is added.
			"""
			)
	public List<Map<String, Object>> createGhost(
			@ToolParam(description = "The name of the ghost (in case the player asks)") String name,
			@ToolParam(description = "The appearance of the ghost that will be described to the player") String appearance,
			@ToolParam(description = "The general mood of the ghost. Is it an angry or happy ghost? Something else? To help you act the ghost.") String character) {
		neo4j.createGhost(player, name, appearance, character);
		return neo4j.getGhosts(player);
	}

	@Tool(description =
			"""
			Updates the opinion a ghost has about the current player as they are having conversations.
			"""
			)
	public String updateGhostPlayerOpinion(
			@ToolParam(description = "The id of the ghost to update for, as received from getGhosts() and createGhost()") String ghostId,
			@ToolParam(description = "The new opinion that the ghost has about the current player") String opinion) {
		return neo4j.updateGhostPlayerOpinion(player, ghostId, opinion);
	}

	@Tool(description =
			"""
			Sets the name, description and available doors for the current room. Should only be called if name and/or description was empty
			when calling getCurrentRoom(). The return value of this is the same as getCurrentRoom() after the details are updated.
			"""
			)
	public Map<String, Object> createRoom(
			@ToolParam(description = "The name of the room, e.g. Library, Kitchen or Bed Room") String name, 
			@ToolParam(description = "A description of the room, in the same literary style as the rooms visited before") String description, 
			@ToolParam(description = "A string with a combination of the characters N, S, E, W describing what new doors to add (only allowed for the directions that were indicated as <Unknown>). Remember that not all unknowns has to get a door, some paths can be closed.") String newDoors) {
		neo4j.createRoom(player, name, description, newDoors);
		return neo4j.getCurrentRoom(player);
	}

	@Tool(description =
			"""
			Move the player into a new room in the direction indicated (has to be a direction where there is a door) as requested by the player.
			This returns the new room just as getCurrentRoom() would
			"""
			)
	public Map<String, Object> move(
			@ToolParam(description = "The direction to move, either \"N\", \"S\", \"W\" or \"E\". There must be a door in that direction.") String direction) {
		neo4j.move(player, direction);
		return neo4j.getCurrentRoom(player);
	}

	@Tool(description =
			"""
			Kills the current player because of something that happened in the room (either if a ghost got angry or something else
			happened, like the player jumping out the window or something like that. Returns an empty string.
			"""
			)
	public String killPlayer(
			@ToolParam(description = "How was the player killed?") String how) {
		killed = how;
		neo4j.killPlayer(player);
		return "";
	}

	public AssistantMessage respond(Conversation conversation) {
		return aiBuilder
				.withShowPrompts(true)
				.ai()
				.withLlm(LlmOptions.withDefaultLlm().withTemperature(null))
				.withToolObject(this)
				.respond(conversation.getMessages());
	}
}