This project is meant to demonstrate the use of AI Agents and Agentic memory (using Neo4j) and the Embabel framework for Java agent implementation.
It implements a simple text based adventure game, set in a haunted castle, with the AI as the Game Master.
You can read more in this blog about the topic: <TBD>

To run it you need some prerequisites:

You need a Neo4j instance. You can setup an Aura instance in the cloud, and an Aura Free instance is sufficient:
https://neo4j.com/product/auradb/

In the Query window of Aura (where you can run Cypher queries directly), run this query to load the initial setup of the game:
CREATE (r:Room:Original:Start {name: "Porch", description: "You find yourself standing on the creaking wooden porch of an enormous, ancient castle, its weathered stone walls looming ominously in the misty twilight. Before you rises a towering wooden door, dark and heavy with age, its iron hinges rusted and ornate carvings half-faded by time, as though guarding secrets long buried within the castle's shadowed halls.", position: Point({x: 0, y: 0})})
CREATE (r2:Room:Original {name: "", description: "", position: Point({x: 0, y: 1})})
CREATE (r)-[:DOOR {direction: "N"}]->(r2)
CREATE (r2)-[:DOOR {direction: "S"}]->(r)

If you have played the game and want to reset it to the original state you can do it with this query:
MATCH (n:!Original) DETACH DELETE n;
MATCH (r:!Start) SET r.name = "", r.description = "";

You will also need to have an OpenAI account and set up an API key.

Before you launch the application you will need to set these ennvironment variables:
DB_URI: The URI to your Neo4j instance, in the format of neo4j+s://<INSTANCEID>.databases.neo4j.io
DB_USER: The database user (the default with an Aura instance is "neo4j")
DB_PWD: The password for that user
DB_NAME: The database name (default is "neo4j")
OPENAI_API_KEY: The API key you created with OpenAI
