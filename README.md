# recru-mothership
PoC for new type of recru task

The idea is for the candidates to write a program that interates with the mothership. 
The mothership is a "game engine" that can run a simple game for up to 6 players. 
Programs written by the candidates are connecting then issuing move orders via REST api and listening on Kafka for updates on board changes.
The candidates goal is to write a program that can integrate and be reasonably efficient in playing against other bots. Both simplistic ones provided by us and bots created by other candidates.
