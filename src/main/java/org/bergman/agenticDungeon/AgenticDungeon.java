package org.bergman.agenticDungeon;

import com.embabel.agent.config.annotation.EnableAgents;
import com.embabel.chat.InMemoryConversation;
import com.embabel.chat.UserMessage;
import com.embabel.common.util.AnsiBuilder;
import com.embabel.common.util.AnsiColor;
import com.embabel.common.util.AnsiStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Scanner;

@SpringBootApplication
@EnableAgents
public class AgenticDungeon {

	@Autowired
	private GameMasterAgent agent;

	private final AnsiBuilder assistantMessageStyle = new AnsiBuilder()
			.withStyle(AnsiColor.RED, AnsiStyle.BOLD);

	private final String systemMessage =
			"""
			You are the game master for a text based adventure game. The game takes place in a huge old haunted castle consisting of rooms
			(only square rooms, no corridors), doors between the rooms and ghosts in some of the rooms that the player can interact with and
			talk to. The tools are used for you to create the rooms and the ghosts, but also as a memory of what already has been created
			and what the ghosts thinks of the player.
			When a new room is entered ask for the details of the room with getCurrentRoom() and check if there are ghosts by calling getGhosts().
			If name or description is empty it means the room hasn't been created, and you need to do so. Each room as four possible doors: north,
			west, east and south. The status for each can be <No door>, <Door> or <Unknown>. If it is <Unknown> it means you can decide if
			there should be a door when you create the room. If you need to create the room, call createRoom() and set a name, a description and
			the status of the <Unknown> doors. When a room is created you can also choose to create ghosts in the room if you want to by calling
			createGhost() (multiple times if you want multiple ghosts).
			When a room is entered (whether new or existing) describe to the player what it looks like, who are there (ghosts) and what doors
			there are to leave the room.
			Listen to what the player does and answer appropriately. If they interact with a ghost then answer as if you were the ghost. After
			each interaction with a ghost call updateGhostPlayerOpinion() to set what they feel about the player right now. If a ghost gets too
			upset, and depending on the character of the ghost, they may kill the player (which you indicate by calling killPlayer().
			If a player says they will go through a door, either by indicating the direction or describing the door, call move() to indicate they
			went to a new room and then start over in a new room (move() will return the same thing as getCurrentRoom() for the new room, so no
			need to call getCurrentRoom() again after moving).
			Always interact with the player as the role playing game master you are. And it is a horror game set in a haunted castle, so keep that
			in mind in the atmosphere.
			Remember that it is you who is the game master and should come up with all new rooms, doors and ghosts, you should never ask the
			player for help with that. Also remember to always call updateGhostPlayerOpinion() when someone has interacted with a ghost.
			Please keep the castle to one floor, our representation only allows for that. So no stairs anywhere.
			""";

	public static void main(String[] args) {
		SpringApplication.run(AgenticDungeon.class, args);
	}

	@Bean
	CommandLineRunner run() {
		return args -> {
			var conversation = InMemoryConversation.withSystemMessage(systemMessage);
			try (Scanner scanner = new Scanner(System.in)) {
				System.out.print(assistantMessageStyle.format("What is your name: "));
				var name = scanner.nextLine();
				agent.createPlayer(name);

				String userInput = "";
				String query = "The player is in the first room now, fetch the details and present it to the player and see what they want to do";
				do {
					conversation = conversation.withMessage(new UserMessage(query));
					var assistantMessage = agent.respond(conversation);
					conversation = conversation.withMessage(assistantMessage);
					System.out.println(assistantMessageStyle.format(assistantMessage.getContent()));

					if (agent.getKilled() == null) {
						userInput = scanner.nextLine();
						query = "Here is the user input. Please act on it and handle room updates if they moved. Remember to update what the ghosts " +
								"thinks of them with updateGhostPlayerOpinion() if they did interact with one: "
								+ userInput;
					}
				} while (agent.getKilled() == null && !userInput.trim().equalsIgnoreCase("exit"));
			}
			System.exit(0);
		};
	}
}