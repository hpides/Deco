<h1 align="center">Efficent Window Aggregation in Decentralized Networks </h1>

# Deco

Deco is also a decentralized approach that is based on Desis. Deco can move calcuations of count-based windows from centers to the local nodes.

# Approach Introduction:
- Deco
    - decentralized aggregation and supports count-based windows    
- Scotty
    - implemented by the `Scotty`
- Disco 
    - implemented by the `Disco`
- Central(DeCen)
    - centralized aggregation and can not perform incremental aggregation.
    
# Installation

- **Requirements**: Java 8+
- **Install**
     1. Download DESengine
     2. Compile Project
     3. Set `WINDOWS = true` in `Configuration.java`
     4. Run OverallMainDriverTest.java

# Input
- Deco
  - Node Id:
    - The id of the node that is deployed with Desis.
  - Local Nodes:
    - How many local nodes. 
  - Query Modes:
    - The query mode is to choose the query pattern that is set into query generation file.
  - Generator Thread Number:
    - How many generator threads are initialized. One thread can produce at least 10 million tuples/s.
