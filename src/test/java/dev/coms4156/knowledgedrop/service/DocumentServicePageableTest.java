package dev.coms4156.knowledgedrop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.storage.FileStorage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AbstractPageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Unit tests for the offset-based {@link Pageable} that {@link DocumentService#list} sends to the
 * repository: navigation methods, sorting, equality, extreme values and repository failures.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServicePageableTest {

  @Mock private DocumentRepository documentRepository;

  @Mock private IngestionService ingestionService;

  @Mock private FileStorage fileStorage;

  @Mock private PlatformTransactionManager transactionManager;

  @InjectMocks private DocumentService documentService;

  private ClientRecord client;
  private UUID clientId;

  @BeforeEach
  void setUp() {
    clientId = UUID.randomUUID();
    client = new ClientRecord("acme", "token-hash");
    ReflectionTestUtils.setField(client, "id", clientId);
  }

  private void stubEmptyPage() {
    when(documentRepository.findByOwnerIdAndDeletedAtIsNull(eq(clientId), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
  }

  /** Lists once and returns the Pageable the repository received. */
  private AbstractPageRequest pageableFor(Integer limit, Integer offset) {
    stubEmptyPage();
    documentService.list(client, null, limit, offset);
    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(documentRepository).findByOwnerIdAndDeletedAtIsNull(eq(clientId), captor.capture());
    return (AbstractPageRequest) captor.getValue();
  }

  // ---------------------------------------------------------------------------------------------
  // navigation
  // ---------------------------------------------------------------------------------------------

  @ParameterizedTest
  @CsvSource({"10,5,15,0", "10,25,35,15", "3,4,7,1", "5,0,5,0"})
  void navigation_nextAndPreviousMoveByPageSizeAndStopAtZero(
      int limit, int offset, long expectedNext, long expectedPrevious) {
    AbstractPageRequest pageable = pageableFor(limit, offset);

    Pageable next = pageable.next();
    Pageable previous = pageable.previous();

    assertEquals(expectedNext, next.getOffset());
    assertEquals(expectedPrevious, previous.getOffset());
    assertEquals(limit, next.getPageSize());
    assertEquals(limit, previous.getPageSize());
  }

  @Test
  void navigation_firstGoesBackToOffsetZeroWithSamePageSize() {
    AbstractPageRequest pageable = pageableFor(10, 25);

    Pageable first = pageable.first();

    assertEquals(0L, first.getOffset());
    assertEquals(10, first.getPageSize());
  }

  @ParameterizedTest
  @CsvSource({"10,5,0,0", "10,5,3,30", "4,0,2,8"})
  void navigation_withPageMultipliesPageNumberByPageSize(
      int limit, int offset, int pageNumber, long expectedOffset) {
    AbstractPageRequest pageable = pageableFor(limit, offset);

    Pageable page = pageable.withPage(pageNumber);

    assertEquals(expectedOffset, page.getOffset());
    assertEquals(limit, page.getPageSize());
  }

  @Test
  void navigation_keepsNewestFirstSortOnDerivedPageables() {
    AbstractPageRequest pageable = pageableFor(10, 5);
    Sort expected = Sort.by(Sort.Direction.DESC, "uploadedAt");

    assertEquals(expected, pageable.getSort());
    assertEquals(expected, pageable.next().getSort());
    assertEquals(expected, pageable.previous().getSort());
    assertEquals(expected, pageable.first().getSort());
    assertEquals(expected, pageable.withPage(2).getSort());
  }

  // ---------------------------------------------------------------------------------------------
  // equality
  // ---------------------------------------------------------------------------------------------

  @Test
  void equality_dependsOnlyOnOffsetAndPageSize() {
    stubEmptyPage();
    documentService.list(client, null, 10, 5);
    documentService.list(client, null, 10, 5);
    documentService.list(client, null, 10, 6);
    documentService.list(client, null, 3, 5);
    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(documentRepository, times(4))
        .findByOwnerIdAndDeletedAtIsNull(eq(clientId), captor.capture());
    List<Pageable> pageables = captor.getAllValues();

    Pageable base = pageables.get(0);
    assertEquals(base, base);
    assertEquals(base, pageables.get(1));
    assertEquals(base.hashCode(), pageables.get(1).hashCode());
    assertNotEquals(base, pageables.get(2));
    assertNotEquals(base, pageables.get(3));
    assertNotEquals(base, PageRequest.of(0, 10));
    assertNotEquals(base, null);
  }

  // ---------------------------------------------------------------------------------------------
  // extreme values and failures
  // ---------------------------------------------------------------------------------------------

  @Test
  void list_maximumLimitAndOffset_arePassedThroughUnchanged() {
    AbstractPageRequest pageable = pageableFor(Integer.MAX_VALUE, Integer.MAX_VALUE);

    assertEquals((long) Integer.MAX_VALUE, pageable.getOffset());
    assertEquals(Integer.MAX_VALUE, pageable.getPageSize());
  }

  @Test
  void list_pageNumberIsAlwaysZeroBecauseOffsetIsAddressedDirectly() {
    AbstractPageRequest pageable = pageableFor(10, 40);

    assertEquals(0, pageable.getPageNumber());
    assertNotNull(pageable.getSort());
  }

  @Test
  void list_repositoryFails_propagatesTheFailureUnwrapped() {
    when(documentRepository.findByOwnerIdAndDeletedAtIsNull(eq(clientId), any(Pageable.class)))
        .thenThrow(new IllegalStateException("db down"));

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> documentService.list(client, null, null, null));

    assertEquals("db down", ex.getMessage());
  }
}
