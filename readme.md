# scala-es-example

This is an example of storage and asynchronous retrieval of Elasticsearch data
using Scala. Elasticsearch data is in the form of minigame metadata.

## Setup

Run `puttemplate.sh`, to store an index template in Elasticsearch.

If you're not on a platform amenable to running shell scripts, you may instead evaluate this request using your favorite Elasticsearch client:

``` json
PUT _template/minigames_template
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
                "averageDuration": {"type": "long"},
                "difficulty": {"type": "integer"},
                "publishTime": {"type": "date"}
            }
        }
    }
}
```

## Suggested testing procedure

- `sbt "run search"` which should report an Elasticsearch error due to no index existing yet.

- `sbt "run populate"` to store some test data in Elasticsearch.

- `sbt "run search"` to list the test data using default search parameters.

- `sbt "run store id1234 \"best game ever\" airplane \"time trial\" user1234 200 100 1234 50 2017-01-01T12:00:00Z"` to store a document in Elasticsearch

- `sbt "run search"` to list all documents, including the one just created.

- `sbt "run search name best"` to list only documents with "best" in their name, such as the example given two steps above.

## Usage

The program accepts either a `populate`, `search`, or `store` argument.

### populate

The program recognizes no additional arguments in this case.

### search

When running with `search`, search parameters may be specified like so, in any combination:

- `name [name]` to search for documents by name

- `gameMode [mode]` to search for documents by an exact match on the `gameMode` field

- `vehicle [vehicle]` to search for documents by an exact match on the `vehicle` field

- `difficulty [min]-[max]` to search for documents with difficulty in the range [min, max]

- `sort [sort]` to define the field to sort by, must be one of `difficulty`, `publishtime`, `rating`

- `sortOrder [order]` to define the sort order, should be either `ascending` or `descending`

- `count [count]` to indicate the number of documents to include in the results

- `offset [offset]` to indicate the offset in the query; in combination with `count` this can be used for pagination

For example:

- `sbt "run search gameMode \"time trial\""` will show results with the game mode "time trial".

- `sbt "run search vehicle boat difficulty 0-50"` will show results where vehicle is "boat" and where difficulty is in the range [0, 50].

- `sbt "run search sort difficulty sortOrder ascending"` will sort results in ascending order of difficulty.

- `sbt "run search count 2 offset 2"` will show the two results starting at offset 2, by way of pagination.

### store

When running with `store`, 10 additional arguments must be included, in this order:

- ID of the document, must be a string

- name, must be a string

- vehicle, must be a string

- game mode, must be a string

- creator ID, must be a string

- times rated, must be an integer

- times rated positively, must be an integer; should be less than or equal to times rated

- duration, must be an integer

- difficulty, must be an integer; should be in the range [0, 100)

- publication time, must be a timestamp; should be in a format e.g. `2017-01-01T12:00:00Z`
