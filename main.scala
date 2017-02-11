import scala.concurrent.{Future, future}
import scala.Enumeration
import scala.concurrent.ExecutionContext.Implicits.global
import java.io.IOException
import com.ning.http.client.Response
import com.github.nscala_time.time.Imports.DateTime
import play.api.libs.json.{Json, JsValue, JsObject, JsArray}
import wabisabi.Client

import java.lang.IllegalArgumentException
import scala.concurrent.Await
import scala.concurrent.duration._



// Class representing a minigame
class Minigame(
    // Minigame's unique ID
    var id: String,
    // Name of the minigame
    var name: String,
    // Vehicle to which the minigame applies
    var vehicle: String,
    // Indication of the minigame's game mode
    var gameMode: String,
    // Unique identifier of who created the minigame
    var creatorId: String,
    // Number of times players have rated the minigame
    var timesRated: Long,
    // Number of times players have positively rated it
    var upThumbs: Long,
    // How long it should be expected to take to complete the minigame
    var averageDuration: Long,
    // A measurement of difficulty in the range [0, 100)
    var difficulty: Int,
    // Representation of when the minigame was published to the server
    var publishTime: DateTime
){
    // Instantiate a minigame object given a parsed json object representing
    // an Elasticsearch document
    def this(document: JsObject) = this(
        id = (document \ "_id").as[String],
        name = (document \ "_source" \ "name").as[String],
        vehicle = (document \ "_source" \ "vehicle").as[String],
        gameMode = (document \ "_source" \ "gameMode").as[String],
        creatorId = (document \ "_source" \ "creatorId").as[String],
        timesRated = (document \ "_source" \ "timesRated").as[Long],
        upThumbs = (document \ "_source" \ "upThumbs").as[Long],
        averageDuration = (document \ "_source" \ "averageDuration").as[Long],
        difficulty = (document \ "_source" \ "difficulty").as[Int],
        publishTime = DateTime.parse((document \ "_source" \ "publishTime").as[String])
    )
    // Get a json representation of this object, not including ID
    def asjson(): JsObject = {
        return Json.obj(
            "name" -> this.name,
            "vehicle" -> this.vehicle,
            "gameMode" -> this.gameMode,
            "creatorId" -> this.creatorId,
            "timesRated" -> this.timesRated,
            "upThumbs" -> this.upThumbs,
            "upThumbsRatio" -> this.upThumbs.asInstanceOf[Double] / this.timesRated,
            "averageDuration" -> this.averageDuration,
            "difficulty" -> this.difficulty,
            "publishTime" -> this.publishTime.toString()
        )
    }
    // Persist the minigame object in Elasticsearch
    def store(esclient: Client): Future[Response] = {
        return esclient.index(
            index = "minigames",
            `type` = "minigame",
            id = Some(this.id),
            data = Json.stringify(this.asjson())
        )
    }
    // Get a string representation of this minigame
    override def toString(): String = {
        return Json.stringify(this.asjson())
    }
}



// Enumeration of sort options
object MinigameSearchSort extends Enumeration{
    val Difficulty, PublishTime, Rating = Value
}
    
// Represents minigame search parameters
class MinigameSearchParams(
    // If not null, include only the results with a matching game mode
    var gameMode: String = null,
    // If not null, include only the results with a matching vehicle
    var vehicle: String = null,
    // If not null, include only the results with a matching name
    var name: String = null,
    // Match minigames only with a difficulty rating of at least this
    var minDifficulty: Int = 0,
    // Match minigames only with a difficulty rating of at most this
    var maxDifficulty: Int = 99,
    // How to sort the results of a query
    var sort: MinigameSearchSort.Value = MinigameSearchSort.Rating,
    // Whether to sort in ascending order (when true) or descending order (when false)
    var sortOrder: Boolean = false,
    // The number and offset of results to query, to enable pagination
    var count: Long = 25,
    var offset: Long = 0
){
    // Build a json object to be used in an Elasticsearch query
    def esquery(): JsObject = {
        var filters = new JsArray()
        if(this.gameMode != null) filters = filters :+ Json.obj(
            "term" -> Json.obj("gameMode" -> this.gameMode)
        )
        if(this.vehicle != null) filters = filters :+ Json.obj(
            "term" -> Json.obj("vehicle" -> this.vehicle)
        )
        if(this.name != null) filters = filters :+ Json.obj(
            "term" -> Json.obj("name" -> this.name)
        )
        if(this.minDifficulty > 0 || this.maxDifficulty < 99) filters = filters :+ Json.obj(
            "range" -> Json.obj(
                "difficulty" -> Json.obj(
                    "gte" -> this.minDifficulty,
                    "lte" -> this.maxDifficulty
                )
            )
        )
        val sortOrder = if(this.sortOrder) "asc" else "desc"
        val sort = this.sort match {
            case MinigameSearchSort.Difficulty => Json.obj(
                "difficulty" -> Json.obj("order" -> sortOrder)
            )
            case MinigameSearchSort.PublishTime => Json.obj(
                "publishTime" -> Json.obj("order" -> sortOrder)
            )
            case MinigameSearchSort.Rating => Json.obj(
                "upThumbsRatio" -> Json.obj("order" -> sortOrder)
            )
        }
        def jsonResult(filter: JsValue): JsObject = {
            return Json.obj(
                "size" -> this.count,
                "from" -> this.offset,
                "sort" -> sort,
                "filter" -> filter
            )
        }
        if(filters.value.size == 0){
            return jsonResult(Json.obj("match_all" -> Json.obj()))
        }else if(filters.value.size == 1){
            return jsonResult(filters(0));
        }else{
            return jsonResult(Json.obj(
                "bool" -> Json.obj("must" -> filters)
            ))
        }
    }
}



object Minigames{
    def search(esclient: Client, params: MinigameSearchParams): Future[Array[Minigame]] = {
        def handleFuture(response: Response): Future[Array[Minigame]] = {
            if(response.getStatusCode != 200){
                throw new IOException(
                    "Elasticsearch query failed: " + response.getResponseBody
                );
            }else{
                return future{
                    (Json.parse(response.getResponseBody) \ "hits" \ "hits").as[Array[JsObject]].map(
                        document => new Minigame(document)
                    )
                }
            }
        }
        return esclient.search(
            index = "minigames",
            `type` = Some("minigame"),
            query = Json.stringify(params.esquery())
        ).flatMap(
            response => handleFuture(response)
        )
    }
    
    // Store a minigame in Elasticsearch and wait for the operation to complete
    def storeMinigame(esclient: Client, minigame: Minigame): Unit = {
        Await.result(minigame.store(esclient), 5000 millis)
    }
    
    def main(args: Array[String]){
        val esclient = new Client("http://localhost:9200")
        
        if(args.length == 0){
            println("Please check readme.md for usage instructions.")
            
        }else if(args(0) == "populate"){
            println("Populating Elasticsearch with some test data.")
            this.storeMinigame(esclient, new Minigame(
                id = "id1",
                name = "hello world",
                vehicle = "car",
                gameMode = "time trial",
                creatorId = "yourself",
                timesRated = 100,
                upThumbs = 50,
                averageDuration = 1234,
                difficulty = 50,
                publishTime = new DateTime("2017-01-01")
            ))
            this.storeMinigame(esclient, new Minigame(
                id = "id2",
                name = "hello again",
                vehicle = "boat",
                gameMode = "time trial",
                creatorId = "yourself",
                timesRated = 200,
                upThumbs = 50,
                averageDuration = 1234,
                difficulty = 25,
                publishTime = new DateTime("2016-01-01")
            ))
            this.storeMinigame(esclient, new Minigame(
                id = "id3",
                name = "hey now you're an all star",
                vehicle = "lawnmower",
                gameMode = "creative",
                creatorId = "steve from smash mouth",
                timesRated = 9000,
                upThumbs = 8999,
                averageDuration = 500,
                difficulty = 20,
                publishTime = new DateTime("1999-01-01")
            ))
            this.storeMinigame(esclient, new Minigame(
                id = "id4",
                name = "hi",
                vehicle = "helicopter",
                gameMode = "time trial",
                creatorId = "yourself",
                timesRated = 500,
                upThumbs = 320,
                averageDuration = 16,
                difficulty = 90,
                publishTime = new DateTime("2014-01-01")
            ))
        
        }else if(args(0) == "store"){
            val minigame = new Minigame(
                id = args(1),
                name = args(2),
                vehicle = args(3),
                gameMode = args(4),
                creatorId = args(5),
                timesRated = args(6).toInt,
                upThumbs = args(7).toInt,
                averageDuration = args(8).toInt,
                difficulty = args(9).toInt,
                publishTime = new DateTime(args(10))
            )
            println("Storing minigame in Elasticsearch: " + minigame.toString())
            this.storeMinigame(esclient, minigame)
            
        }else if(args(0) == "search"){
            var params = new MinigameSearchParams();
            var i = 0
            for(i <- 0 until args.length if i % 2 == 1){
                if(args(i) == "name"){
                    params.name = args(i + 1)
                }else if(args(i) == "gameMode"){
                    params.gameMode = args(i + 1)
                }else if(args(i) == "vehicle"){
                    params.vehicle = args(i + 1)
                }else if(args(i) == "difficulty"){
                    val parts = args(i + 1).split("-")
                    params.minDifficulty = parts(0).toInt
                    params.maxDifficulty = parts(1).toInt
                }else if(args(i) == "sort"){
                    params.sort = args(i + 1) match{
                        case "difficulty" => MinigameSearchSort.Difficulty
                        case "publishTime" => MinigameSearchSort.PublishTime
                        case "rating" => MinigameSearchSort.Rating
                        case _ => throw new IllegalArgumentException()
                    }
                }else if(args(i) == "sortOrder"){
                    params.sortOrder = args(i + 1) == "ascending"
                }else if(args(i) == "count"){
                    params.count = args(i + 1).toInt
                }else if(args(i) == "offset"){
                    params.offset = args(i + 1).toInt
                }
            }
            
            println("Performing search...")
            
            val future = Minigames.search(esclient, params)
            future.onFailure{
                case exc => println("Search failed: " + exc.getMessage)
            }
            future.onSuccess{
                case results => println(
                    "Search succeeded, found " + results.length + " results:\n" + results.mkString("\n")
                )
            }
            Await.result(future, 5000 millis)
        }
        Client.shutdown()
    }
}
