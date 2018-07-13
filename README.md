# recru-mothership
PoC for new type of recru task

The idea is for the candidates to write a program that interates with the mothership. 
The mothership is a "game engine" that can run a simple game for up to 6 players. 
Programs written by the candidates are connecting then issuing move orders via REST api and listening on Kafka for updates on board changes.
The candidates goal is to write a program that can integrate and be reasonably efficient in playing against other bots. Both simplistic ones provided by us and bots created by other candidates.

Status: early PoC


## Testing locally

First start the dependencies with `docker-compose up`

Create a game:

```
curl -X POST \
  http://localhost:8080/game/ \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json' \
  -d '{"name": "player1"}'
```
  
from the response above you should extract the game id.

Make a move in the game:

```
curl -X POST \
  http://localhost:8080/game/5ecd57f8-af16-49da-98e5-d969e872e020 \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json' \
  -d '{"name": "player1", "color": "red", "move": 2}'
  ```

## Listen on Kafka:

Connect to the container: `sudo docker exec -i -t {container_id} /bin/bash`

Then `cd /opt/kafka_2.11-0.10.1.0/bin`

Listen on the "colloseum" topic: `./kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic colloseum --from-beginning`