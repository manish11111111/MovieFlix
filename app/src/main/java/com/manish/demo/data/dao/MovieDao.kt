package com.manish.demo.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.manish.demo.entities.GenreEntity
import com.manish.demo.entities.LanguageEntity
import com.manish.demo.entities.MovieEntity
import com.manish.demo.entities.MovieGenreCrossRef
import com.manish.demo.entities.MovieLanguageCrossRef

@Dao
interface MovieDao {

    // -------------------------------
    // MOVIE CRUD
    // -------------------------------

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertMovie(movie: MovieEntity): Long

    @Update
    suspend fun updateMovie(movie: MovieEntity)

    @Delete
    suspend fun deleteMovie(movie: MovieEntity)

    @Query("SELECT * FROM movies")
    suspend fun getAllMovies(): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE movieId = :id")
    suspend fun getMovieById(id: Long): MovieEntity?


    // -------------------------------
    // GENRES
    // -------------------------------

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertGenre(genre: GenreEntity): Long

    @Query("SELECT * FROM genres")
    suspend fun getAllGenres(): List<GenreEntity>


    // -------------------------------
    // LANGUAGES
    // -------------------------------

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertLanguage(language: LanguageEntity): Long

    @Query("SELECT * FROM languages")
    suspend fun getAllLanguages(): List<LanguageEntity>


    // -------------------------------
    // MOVIE <-> GENRE CROSS REF
    // -------------------------------

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun addGenreToMovie(ref: MovieGenreCrossRef)

    @Query("""
        SELECT g.* FROM genres g
        INNER JOIN movie_genres mg ON g.genreId = mg.genreId
        WHERE mg.movieId = :movieId
    """)
    suspend fun getGenresForMovie(movieId: Long): List<GenreEntity>


    // -------------------------------
    // MOVIE <-> LANGUAGE CROSS REF
    // -------------------------------

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun addLanguageToMovie(ref: MovieLanguageCrossRef)

    @Query("""
        SELECT l.* FROM languages l
        INNER JOIN movie_languages ml ON l.languageId = ml.languageId
        WHERE ml.movieId = :movieId
    """)
    suspend fun getLanguagesForMovie(movieId: Long): List<LanguageEntity>
}