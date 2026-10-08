package com.example.moodify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter that binds a list of Song objects to rows in the RecyclerView.
 * Each row shows the album cover, song title, and artist.
 */
class SongAdapter(
    private val songList: List<Song>,
    private val onSongClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val albumArt: ImageView = itemView.findViewById(R.id.imageAlbumArt)
        val title: TextView = itemView.findViewById(R.id.textSongTitle)
        val artist: TextView = itemView.findViewById(R.id.textSongArtist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songList[position]
        holder.albumArt.setImageResource(song.albumArtResId)
        holder.title.text = song.title
        holder.artist.text = song.artist

        holder.itemView.setOnClickListener {
            onSongClick(song)
        }
    }

    override fun getItemCount(): Int = songList.size
}
