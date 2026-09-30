package com.knowledgegraph.assistant.quality;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QualityEvidenceVerifierTest {
    private static final Scope SCOPE = Scope.project();
    private static final String HASH = "a".repeat(64);
    private final QualityReadRepository repository = mock(QualityReadRepository.class);
    private final SourceLocator locator = mock(SourceLocator.class);
    private final QualityEvidenceVerifier verifier = new QualityEvidenceVerifier(repository, locator);

    @Test void checksOwnerBeforeReadingAndNeverReturnsAnotherDocumentsContent() {
        when(repository.chunkDocumentId(7)).thenReturn(Optional.of(99L));
        var check = verifier.check(SCOPE, 1, 7, HASH, "source:7", 600);
        assertEquals("WRONG_DOCUMENT", check.status());
        assertNull(check.evidence());
        verifyNoInteractions(locator);
    }

    @Test void staleHashCannotBePassedAsCurrentEvidence() {
        source(7, "b".repeat(64), false);
        var check = verifier.check(SCOPE, 1, 7, HASH, "source:7", 600);
        assertEquals("STALE", check.status());
        assertNull(check.content());
        assertNull(check.evidence());
        assertFalse(check.verifiedIdentity());
    }

    @Test void missingChunkCannotBecomeEvidence() {
        when(repository.chunkDocumentId(7)).thenReturn(Optional.empty());
        var check = verifier.check(SCOPE, 1, 7, HASH, "source:7", 600);
        assertEquals("MISSING", check.status());
        assertNull(check.evidence());
        verifyNoInteractions(locator);
    }

    @Test void fullReadUsesRealUnitAndHashButTruncatedTextRemainsPartial() {
        source(7, HASH, false);
        var current = verifier.check(SCOPE, 1, 7, HASH, "source:7", 600);
        assertEquals("CURRENT", current.status());
        assertEquals(3, current.unitIndex());
        assertEquals("第3页", current.locator());
        assertEquals(HASH, current.evidence().object().version());

        when(locator.chunk(SCOPE, 1, 7, 600)).thenReturn(Optional.of(
                new SourceLocator.Chunk(7, 5, 0, HASH, 0, 700, "正文".repeat(300), true)));
        var truncated = verifier.check(SCOPE, 1, 7, HASH, "source:7", 600);
        assertEquals("TRUNCATED", truncated.status());
        assertTrue(truncated.truncated());
        assertTrue(truncated.verifiedIdentity());
    }

    @Test void unitMetadataMustBelongToSameDocument() {
        when(repository.chunkDocumentId(7)).thenReturn(Optional.of(1L));
        when(locator.chunk(SCOPE, 1, 7, 600)).thenReturn(Optional.of(
                new SourceLocator.Chunk(7, 5, 0, HASH, 0, 2, "正文", false)));
        when(repository.unitMeta(SCOPE, 1, 5)).thenReturn(Optional.of(
                new QualityReadRepository.UnitMeta(2, 5, 3, "第3页")));
        var check = verifier.check(SCOPE, 1, 7, HASH, "source:7", 600);
        assertEquals("UNIT_UNAVAILABLE", check.status());
        assertNull(check.evidence());
    }

    @Test void locatorMustReturnTheRequestedRealChunkId() {
        when(repository.chunkDocumentId(7)).thenReturn(Optional.of(1L));
        when(locator.chunk(SCOPE, 1, 7, 600)).thenReturn(Optional.of(
                new SourceLocator.Chunk(8, 5, 0, HASH, 0, 2, "正文", false)));
        var check = verifier.check(SCOPE, 1, 7, HASH, "source:7", 600);
        assertEquals("IDENTITY_MISMATCH", check.status());
        assertNull(check.evidence());
        verify(repository, never()).unitMeta(any(), anyLong(), anyLong());
    }

    private void source(long chunkId, String hash, boolean truncated) {
        when(repository.chunkDocumentId(chunkId)).thenReturn(Optional.of(1L));
        when(locator.chunk(SCOPE, 1, chunkId, 600)).thenReturn(Optional.of(
                new SourceLocator.Chunk(chunkId, 5, 0, hash, 0, 2, "正文", truncated)));
        when(repository.unitMeta(SCOPE, 1, 5)).thenReturn(Optional.of(
                new QualityReadRepository.UnitMeta(1, 5, 3, "第3页")));
    }
}
