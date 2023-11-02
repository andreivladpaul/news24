package com.androiddevs.mvvmnewsapp.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.*
import android.net.NetworkCapabilities.*
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androiddevs.mvvmnewsapp.model.Article
import com.androiddevs.mvvmnewsapp.model.NewsResponse
import com.androiddevs.mvvmnewsapp.repository.NewsRepository
import com.androiddevs.mvvmnewsapp.util.Resource
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.IOException
import kotlin.reflect.typeOf

class NewsViewModel(app: Application, val newsRepository : NewsRepository) : AndroidViewModel(app) {

    val breakingNews : MutableLiveData<Resource<NewsResponse>> = MutableLiveData()
    var breakingNewsPage = 1
    var breakingNewsResponse: NewsResponse? = null

    val searchNews : MutableLiveData<Resource<NewsResponse>> = MutableLiveData()
    var searchNewsPage = 1
    var searchNewsResponse: NewsResponse? = null

    init {
        getBreakingNews("us")
    }

    fun getBreakingNews(countryCode: String) = viewModelScope.launch {
        safeBreakingNewsCall(countryCode)
    }

    private fun handleBreakingNewsResponse(response : Response<NewsResponse>) : Resource<NewsResponse> {
        if(response.isSuccessful) {
            response.body()?.let { resultResponse ->
                breakingNewsPage++
                if(breakingNewsResponse == null) {
                    breakingNewsResponse = resultResponse
                } else {
                    val oldArticles = breakingNewsResponse?.articles
                    val newArticles = resultResponse.articles
                    oldArticles?.addAll(newArticles)
                }
                return Resource.Success(breakingNewsResponse ?: resultResponse)
            }
        }
        return Resource.Error(response.message())
    }

    fun searchNews(searchedQuery: String) = viewModelScope.launch {
        safeSearchNewsCall(searchedQuery)
    }

    private fun handleSearchNewsResponse(response : Response<NewsResponse>, searchedQuery : String, oldSearQuery : String) : Resource<NewsResponse> {
        var oldSearch : MutableList<Article>? = null
        if(response.isSuccessful) {
            response.body()?.let { resultResponse ->
                searchNewsPage++
                if(searchNewsResponse == null) {
                    searchNewsResponse = resultResponse
                } else {
                    if (oldSearQuery != searchedQuery) {
                        searchNewsResponse?.articles?.clear()
                    }
                    oldSearch = searchNewsResponse?.articles
                    val newSearch = resultResponse.articles
                    oldSearch?.addAll(newSearch)
                }
                return Resource.Success(searchNewsResponse ?: resultResponse)
            }
        }
        return Resource.Error(response.message())
    }

    fun savedArticle(article: Article) = viewModelScope.launch {
        newsRepository.upsert(article)
    }
    fun getSavedNews() = newsRepository.getSavedNews()

    fun deleteArticle(article: Article) = viewModelScope.launch {
        newsRepository.deleteArticle(article)
    }

    private fun hasInternetConnection() :Boolean {
        val connectivityManager = getApplication<NewsApplication>().getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager
        //M is API 23
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capacbilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return  when{
                capacbilities.hasTransport(TRANSPORT_WIFI) -> true
                capacbilities.hasTransport(TRANSPORT_CELLULAR) -> true
                capacbilities.hasTransport(TRANSPORT_ETHERNET) -> true
                else -> false

            }

        } else {
            connectivityManager.activeNetworkInfo?.run {
                return  when(type){
                    TYPE_WIFI -> true
                    TYPE_MOBILE -> true
                    TYPE_ETHERNET -> true
                    else -> false

                }
            }
        }
        return false
    }

    private  suspend fun safeBreakingNewsCall(countryCode: String){
        breakingNews.postValue(Resource.Loading())
        try{
            if (hasInternetConnection()){
                val response = newsRepository.getBreakingNews(countryCode, breakingNewsPage)
                breakingNews.postValue(handleBreakingNewsResponse(response))
            } else {
                breakingNews.postValue(Resource.Error("No internet connection"))
            }

        }catch (t: Throwable){
            when(t) {
                is IOException -> breakingNews.postValue(Resource.Error("Network Failure"))
                else -> breakingNews.postValue(Resource.Error("Conversion error"))
            }
        }
    }

    private  suspend fun safeSearchNewsCall(searchedQuery: String){
        var oldSearchQuery = ""

        searchNews.postValue(Resource.Loading())
        try{
            if (hasInternetConnection()){
                val response = newsRepository.searchNews(searchedQuery, searchNewsPage)
                searchNews.postValue(handleSearchNewsResponse(response,searchedQuery, oldSearchQuery ))
            } else {
                searchNews.postValue(Resource.Error("No internet connection"))
            }

        }catch (t: Throwable){
            when(t) {
                is IOException -> searchNews.postValue(Resource.Error("Network Failure"))
                else -> searchNews.postValue(Resource.Error("Conversion error"))
            }
        }

        if (oldSearchQuery != searchedQuery)
            oldSearchQuery = searchedQuery
    }
}