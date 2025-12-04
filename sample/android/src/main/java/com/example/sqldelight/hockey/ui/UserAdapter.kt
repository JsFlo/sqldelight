package com.example.sqldelight.hockey.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.example.sqldelight.hockey.R
import com.example.sqldelight.hockey.data.User

class UserAdapter(
    private val context: Context,
    private val users: MutableList<User>
) : BaseAdapter() {

    override fun getCount(): Int = users.size

    override fun getItem(position: Int): User = users[position]
  override fun getItemId(p0: Int): Long = users[p0.toInt()].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.user_row, parent, false)

        val user = getItem(position)
        val userNameTextView = view.findViewById<TextView>(R.id.userNameTextView)
        userNameTextView.text = user.name

        return view
    }

    fun updateUsers(newUsers: List<User>) {
        users.clear()
        users.addAll(newUsers)
        notifyDataSetChanged()
    }
}
