package hu.bme.aut.android.pathfindingvisualizer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.room.Room
import hu.bme.aut.android.pathfindingvisualizer.sqlite.Cell
import hu.bme.aut.android.pathfindingvisualizer.sqlite.GridDatabase
import hu.bme.aut.android.pathfindingvisualizer.view.GridView.Algorithms
import hu.bme.aut.android.pathfindingvisualizer.view.GridView.CursorType.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var database: GridDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        database = Room.databaseBuilder(
            applicationContext,
            GridDatabase::class.java,
            "grid-data"
        ).build()
        canvas.mainActivity = this
//        val preferences = getPreferences(Context.MODE_PRIVATE)
        val gridSize = 70 //preferences.getInt("grid_size", 55)
        val algorithm = "DIJKSTRA" //preferences.getString("algorithm", "DIJKSTRA")
        //TODO: ez egyelőre csak simán visszatér a default értékkel
        // és semmi köze nincs a shared preferenceshez
        Log.println(Log.INFO, "pref", "gridsize = $gridSize, algorithm = $algorithm")

        canvas.doOnLayout {
            thread {
                val cells = database.gridDAO().getAllCells()
                canvas.initializeColors(gridSize)
                canvas.restoreObjects(cells, gridSize)
                canvas.currentAlgorithmType = when (algorithm) {
                    "DIJKSTRA" -> Algorithms.DIJKSTRA
                    "ASTAR" -> Algorithms.ASTAR
                    "BELLMANFORD" -> Algorithms.BELLMANFORD
                    else -> error("unknown algorithm type $algorithm")
                }
            }
        }
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setMessage("Biztosan ki akarsz lépni?")
            .setPositiveButton("Igen") { _, _ -> onExit() }
            .setNegativeButton("Mégsem", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        startActivity(Intent(this, SettingsActivity::class.java))
        return true
    }

    private fun onExit() {
        finish()
    }

    fun updateCell(cell: Cell) {
        database.gridDAO().updateCell(cell)
    }

    fun onCursorTypeClicked(view: View) {
        if (view !is ToggleButton) return
        if (!view.isChecked) {
            view.isChecked = true
            return
        }
        setOthersUnchecked(view)
        canvas.currentCursorType = when (view) {
            weight_button -> WEIGHT_TYPE
            wall_button -> WALL_TYPE
            start_node_button -> START_TYPE
            end_node_button -> END_TYPE
            clear_type_button -> CLEAR_TYPE
            else -> error("?")
        }

    }

    private fun setOthersUnchecked(button: ToggleButton) {
        val buttons = listOf(
            wall_button,
            weight_button,
            clear_type_button,
            start_node_button,
            end_node_button
        )
        buttons.forEach {
            if (it != button) {
                it.isChecked = false
            }
        }
    }

    fun onSettingsClicked(view: View) {
        canvas.showAlgo()
    }

    fun insertCell(cell: Cell) {
        database.gridDAO().insertCell(cell)
    }

    fun clearCells() {
        thread {
            database.gridDAO().deleteAllCells()
        }.join(1000)
    }
}