API Documentation - http://localhost:8080/swagger-ui/index.html

Actuator - http://localhost:8080/actuator

Metrics - http://localhost:8080/actuator/metrics

Health - http://localhost:8080/actuator/health

Next steps:
- Rewrite fts with pg tsvector or ElasticSearch
- Benchmark cache, consider using Caffeine or alternative
- Rewrite DB cleanup with pg_cron and vacuum
- Add application metrics - request time, cache hit/miss, etc