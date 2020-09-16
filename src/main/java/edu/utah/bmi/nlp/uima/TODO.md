## TODO in v3.1.0

1. Add solr enabled reader to speed up the development phase, which often need to run corpus many times.
2. Remove cross document inference ae outside the uima pipeline, 
3. Add them as a separate "pipeline" in writer's collectionProcessComplete method, 
4. Enable the collectionProcessComplete's pipeline configurable.
