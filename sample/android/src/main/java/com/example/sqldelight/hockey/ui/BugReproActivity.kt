package com.example.sqldelight.hockey.ui

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ListView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.sqldelight.hockey.R
import com.example.sqldelight.hockey.data.Db
import com.example.sqldelight.hockey.data.User
import com.example.sqldelight.hockey.data.getInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bug Repro Activity for SQLDelight Mapper NPE
 *
 * This reproduces a crash in SQLDelight when:
 * - A SELECT query is observed as a Flow
 * - Rows are deleted concurrently from the same table
 *
 * The generated mapper uses !! for NOT NULL columns. Under a specific read/delete race,
 * the underlying Cursor fails to read a row, and the mapper crashes with an NPE.
 *
 * Related: https://github.com/sqldelight/sqldelight/issues/4194
 */
class BugReproActivity : ComponentActivity() {

    companion object {
        private const val TAG = "BugReproActivity"
        private const val USERS_TO_INSERT = 50000
        private const val USER_NAME_LENGTH = 500
    }

    private lateinit var userListView: ListView
    private lateinit var adapter: UserAdapter
    private val users = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bug_repro_activity)

        userListView = findViewById(R.id.userListView)
        adapter = UserAdapter(this, users)
        userListView.adapter = adapter

        val insertManyUsersButton = findViewById<Button>(R.id.insertManyUsersButton)
        val bugDeleteButton = findViewById<Button>(R.id.bugDeleteButton)

        insertManyUsersButton.setOnClickListener { insertManyUsers() }
        bugDeleteButton.setOnClickListener { randomDeleteWithDelays() }

        // BUG: Observe user list changes with a generated query with mapper that uses cursor.get*()!!
        observeUsers()

        // Hack: Not a fix but helpful to see that we could make queries where SQLDelight doesn't generate cursor.get()!!
        // This usually requires forcefully/tricking SqlDelight into generating nullable fields for non-null fields
        // So that the query mapper does not have !!.
        // THIS IS NOT ADVISED, but comment out observeUsers and uncomment this to see that there will be no crash
//        observeUsersWithGeneratedFieldsThatDontForceNonNull()
    }

    /**
     * This uses the Select query that generates this mapper.
     *
     * Because id and name are non-null, the generated code uses !! and this is where the crash will
     * occur after attempting to read a row that has been deleted.
     *
     * Note: Cursor is not null, the return of the cursor.getLong/getString is null.
     * ```
     * "SELECT User.id, User.name FROM User") { cursor ->
     *     mapper(
     *       cursor.getLong(0)!!,
     *       cursor.getString(1)!!
     *     )
     * ```
     */
    private fun observeUsers() {
        lifecycleScope.launch {
            Db.getInstance(applicationContext).userQueries
                .selectAll()
                .asFlow()
                .mapToList(Dispatchers.IO)
                .collect { userList ->
                    adapter.updateUsers(userList)
                }
        }
    }

    /**
     * This function uses a SELECT query with a hack~ to make SQLDelight generate a mapper without
     * !!.
     *
     * All this does is allow application level code handle this case but this is not a good
     * solution because it requires generated code knowledge and requires hacks for non-null fields.
     * ```
     *  mapper(
     *       cursor.getLong(0),
     *       cursor.getString(1)
     *     )
     * ```
     */
    private fun observeUsersWithGeneratedFieldsThatDontForceNonNull() {
        lifecycleScope.launch {
            Db.getInstance(applicationContext).userQueries
                .selectAllWithHackNullableFields()
                .asFlow()
                .mapToList(Dispatchers.IO)
                .collect { userList ->
                    adapter.updateUsers(
                        userList.mapNotNull { user ->
                            if (user.nullable_id == null || user.nullable_name == null) {
                                Log.d(TAG, "Would have crashed - detected null in non-null field")
                                null
                            } else {
                                User(user.nullable_id!!, user.nullable_name!!)
                            }
                        }
                    )
                }
        }
    }

    // Deletes 10 random rows, delay and 10 is arbitrary making it more likely to run into error
    private fun randomDeleteWithDelays() {
        lifecycleScope.launch {
            repeat(10) {
                if (users.isNotEmpty()) {
                    Db.getInstance(applicationContext).userQueries.deleteUser(users.random().id)
                    delay(100)
                }
            }
        }
    }

    private fun insertManyUsers() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = Db.getInstance(applicationContext)
            for (i in 1..USERS_TO_INSERT) {
                db.userQueries.insertUser("User $i ${"a".repeat(USER_NAME_LENGTH)}")
            }
            Log.d(TAG, "Inserted $USERS_TO_INSERT users")
        }
    }
}
