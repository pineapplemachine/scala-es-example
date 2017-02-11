curl -XPUT "http://localhost:9200/_template/minigames_template" -d'
{
    "template": "minigames",
    "mappings": {
        "minigame": {
            "properties": {
                "name": {"type": "string", "index": "analyzed"},
                "vehicle": {"type": "string", "index": "not_analyzed"},
                "gameMode": {"type": "string", "index": "not_analyzed"},
                "creatorId": {"type": "string", "index": "not_analyzed"},
                "timesRated": {"type": "long"},
                "upThumps": {"type": "long"},
                "upThumbsRatio": {"type": "double"},
                "averageDuration": {"type": "long"},
                "difficulty": {"type": "integer"},
                "publishTime": {"type": "date"}
            }
        }
    }
}'