package hu.bme.aut.android.pathfindingvisualizer.sqlite

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Cell::class], version = 1)
@TypeConverters(Converters::class)
abstract class GridDatabase : RoomDatabase(){
    abstract fun gridDAO(): GridDAO
}