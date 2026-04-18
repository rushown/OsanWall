package com.merowall.ui.home

import app.cash.turbine.test
import com.merowall.data.model.*
import com.merowall.data.repository.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val mockPostRepo = mockk<PostRepository>(relaxed = true)
    private val mockAuthRepo = mockk<AuthRepository>(relaxed = true)
    private val mockMediaRepo = mockk<MediaRepository>(relaxed = true)

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { mockAuthRepo.currentUserId } returns "test_user_id"
        every { mockMediaRepo.getTrendingMovies() } returns flowOf(Result.Success(emptyList()))
        viewModel = HomeViewModel(mockPostRepo, mockAuthRepo, mockMediaRepo)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has no error`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.error)
            assertFalse(state.isRefreshing)
        }
    }

    @Test
    fun `likePost calls repository`() = runTest {
        viewModel.likePost("post123", 5, false)
        coVerify { mockPostRepo.likePost("post123", "test_user_id", true) }
    }

    @Test
    fun `clearError clears error state`() = runTest {
        viewModel.clearError()
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.error)
        }
    }
}
