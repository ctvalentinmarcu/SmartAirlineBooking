# SmartAirlineBooking

This is a simple distributed system which permits the clients to book travels that involve multiple flights. Each airport maintains its own collection of servers and is able to wisely route requests and replies between any nodes in the network. All the airports are assigned to geographical zones, which are used by the routing policies, resulting in a smaller routing overhead. In order to avoid broadcasting queries to all its neighbours, each airport forwards them only to neighbours placed within the next-hop zones towards the destination.


