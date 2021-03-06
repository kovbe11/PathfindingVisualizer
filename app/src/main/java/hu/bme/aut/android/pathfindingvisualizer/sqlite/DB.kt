package hu.bme.aut.android.pathfindingvisualizer.sqlite

import androidx.room.*

@Database(entities = [Cell::class], version = 1)
@TypeConverters(Converters::class)
abstract class GridDatabase : RoomDatabase() {
    abstract fun gridDAO(): GridDAO
}

@Dao
interface GridDAO {
    @Query("SELECT * FROM Cells")
    fun getAllCells(): List<Cell>

    @Insert
    fun insertCell(cell: Cell): Long

    @Update
    fun updateCell(cell: Cell)

    @Delete
    fun deleteCell(cell: Cell)

    @Query("DELETE FROM Cells;")
    fun deleteAllCells()

    fun insertCells(cells: Iterable<Cell>){
        cells.forEach {
            insertCell(it)
        }
    }

}