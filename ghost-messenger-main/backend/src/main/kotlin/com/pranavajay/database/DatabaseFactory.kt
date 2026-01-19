package com.pranavajay.database

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.server.application.*

object DatabaseFactory {
    private lateinit var database: MongoDatabase
    
    fun init(environment: ApplicationEnvironment) {
        val uri = environment.config.propertyOrNull("mongodb.uri")?.getString() 
            ?: "mongodb://localhost:27017"
        val dbName = environment.config.propertyOrNull("mongodb.database")?.getString() 
            ?: "ghost_messenger"
        
        val client = MongoClient.create(uri)
        database = client.getDatabase(dbName)
    }
    
    fun getDatabase(): MongoDatabase = database
}
