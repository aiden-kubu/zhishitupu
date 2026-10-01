import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, type LocationQuery } from 'vue-router'
import { ApiError } from '@/services/http'
import { assistantApi } from '@/services/assistantApi'
import type {
  Artifact,
  Capabilities,
  DocumentOption,
  LibraryOption,
  Run,
  ScopePage,
  Template,
} from '@/services/assistantTypes'
import { errorMessage } from './presentation'

export function parseReturnLocation(query: LocationQuery) {
  if (!('runId' in query) && !('artifactId' in query)) return { kind: 'normal' } as const
  const valid = (value: unknown): value is string =>
    typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/.test(value)
  if (!valid(query.runId) || !valid(query.artifactId)) return { kind: 'invalid' } as const
  return { kind: 'result', runId: query.runId, artifactId: query.artifactId } as const
}

export function useAssistantWorkbench() {
  const route = useRoute()
  const returnLocation = computed(() => parseReturnLocation(route.query))
  const returnTargeted = computed(() => returnLocation.value.kind !== 'normal')
  const restoring = ref(false),
    restoreError = ref('')
  const capabilities = ref<Capabilities | null>(null)
  const template = ref<Template | null>(null)
  const serviceLoading = ref(false),
    serviceError = ref('')
  const libraries = ref<ScopePage<LibraryOption> | null>(null),
    documents = ref<ScopePage<DocumentOption> | null>(null)
  const libraryLoading = ref(false),
    documentLoading = ref(false),
    libraryError = ref(''),
    documentError = ref('')
  const selectedLibraries = ref<LibraryOption[]>([]),
    selectedDocuments = ref<DocumentOption[]>([])
  const query = ref(''),
    goal = ref(''),
    run = ref<Run | null>(null)
  const creating = ref(false),
    cancelling = ref(false),
    polling = ref(false),
    runError = ref(''),
    notice = ref('')
  const runMissing = ref(false),
    submissionUncertain = ref(false)
  const artifact = ref<Artifact | null>(null),
    artifactLoading = ref(false),
    artifactError = ref(''),
    selectedArtifactId = ref('')
  let disposed = false,
    contextGeneration = 0,
    serviceGeneration = 0,
    libraryGeneration = 0,
    documentGeneration = 0,
    artifactGeneration = 0
  let pollTimer: ReturnType<typeof setTimeout> | undefined
  const controllers = new Set<AbortController>()
  async function call<T>(operation: (signal: AbortSignal) => Promise<T>) {
    const controller = new AbortController()
    controllers.add(controller)
    try {
      return await operation(controller.signal)
    } finally {
      controllers.delete(controller)
    }
  }
  const active = computed(
    () => !!run.value && !runMissing.value && ['running', 'cancelling'].includes(run.value.status),
  )
  const selectionLocked = computed(
    () =>
      returnTargeted.value ||
      restoring.value ||
      active.value ||
      creating.value ||
      submissionUncertain.value,
  )
  const canReadScope = computed(
    () =>
      capabilities.value?.databaseIdentityVerified === true &&
      capabilities.value.capabilities.find((c) => c.id === 'ingestion_read')?.status ===
        'available',
  )
  const canStart = computed(
    () =>
      !selectionLocked.value &&
      !serviceLoading.value &&
      !libraryLoading.value &&
      !documentLoading.value &&
      template.value?.status === 'available' &&
      canReadScope.value &&
      ['ingestion_read', 'research_graph', 'citation_check'].every(
        (id) => capabilities.value?.capabilities.find((c) => c.id === id)?.status === 'available',
      ) &&
      selectedLibraries.value.length > 0 &&
      selectedLibraries.value.length <= 1000 &&
      selectedDocuments.value.length >= 1 &&
      selectedDocuments.value.length <= 2 &&
      query.value.trim().length >= 2 &&
      query.value.trim().length <= 200 &&
      goal.value.trim().length <= 4000,
  )

  async function loadService() {
    const generation = ++serviceGeneration
    serviceLoading.value = true
    serviceError.value = ''
    capabilities.value = null
    template.value = null
    const results = await Promise.allSettled([
      call((signal) => assistantApi.capabilities(signal)),
      call((signal) => assistantApi.templates(signal)),
    ])
    if (disposed || generation !== serviceGeneration) return null
    const [capResult, templateResult] = results
    const errors: string[] = []
    if (capResult.status === 'fulfilled') capabilities.value = capResult.value
    else errors.push(errorMessage(capResult.reason))
    if (templateResult.status === 'fulfilled') template.value = templateResult.value.items[0]
    else errors.push(errorMessage(templateResult.reason))
    serviceError.value = [...new Set(errors)].join(' ')
    serviceLoading.value = false
    if (canReadScope.value && !returnTargeted.value) await loadLibraries(1)
    return capabilities.value
  }

  function assertRunIdentity(data: Run, id: string, hostId: string) {
    if (
      data.runId !== id ||
      data.hostInstanceId !== hostId ||
      new Set(data.tasks.map((task) => task.taskId)).size !== data.tasks.length
    )
      throw new ApiError('BAD_RESPONSE', '运行或宿主身份与当前定位不一致。', 200)
  }
  async function restoreFromLocation() {
    const location = returnLocation.value,
      version = ++contextGeneration
    stopPoll()
    controllers.forEach((controller) => controller.abort())
    ++serviceGeneration
    ++libraryGeneration
    ++documentGeneration
    ++artifactGeneration
    restoring.value = location.kind === 'result'
    restoreError.value = ''
    run.value = null
    artifact.value = null
    selectedArtifactId.value = ''
    runError.value = ''
    artifactError.value = ''
    notice.value = ''
    runMissing.value = false
    submissionUncertain.value = false
    creating.value = false
    cancelling.value = false
    polling.value = false
    artifactLoading.value = false
    libraryLoading.value = false
    documentLoading.value = false
    capabilities.value = null
    template.value = null
    serviceLoading.value = false
    serviceError.value = ''
    libraries.value = null
    documents.value = null
    libraryError.value = ''
    documentError.value = ''
    selectedLibraries.value = []
    selectedDocuments.value = []
    query.value = ''
    goal.value = ''
    if (location.kind === 'invalid') {
      restoreError.value = '原成果定位参数错误：runId 与 artifactId 必须同时为单个合法标识。'
      return
    }
    if (location.kind === 'normal') {
      await loadService()
      return
    }
    try {
      const host = await loadService()
      if (disposed || version !== contextGeneration) return
      if (!host) {
        restoreError.value = serviceError.value || '无法确认当前宿主，原成果未恢复。'
        return
      }
      const data = await call((signal) => assistantApi.run(location.runId, signal))
      if (disposed || version !== contextGeneration) return
      assertRunIdentity(data, location.runId, host.hostInstanceId)
      if (data.tasks.filter((task) => task.artifactId === location.artifactId).length !== 1)
        throw new ApiError('BAD_RESPONSE', '定位成果未唯一关联到原运行任务。', 200)
      selectedArtifactId.value = location.artifactId // 禁止 acceptRun 选择另一成果兜底。
      query.value = data.query
      goal.value = data.goal ?? ''
      acceptRun(data)
      const loaded = await loadArtifact(location.artifactId)
      if (disposed || version !== contextGeneration) return
      if (!loaded)
        throw new ApiError('RESTORE_FAILED', artifactError.value || '原成果未恢复。', 200)
    } catch (error) {
      if (disposed || version !== contextGeneration) return
      stopPoll()
      run.value = null
      artifact.value = null
      restoreError.value =
        error instanceof ApiError && error.code === 'RESTORE_FAILED'
          ? error.message
          : errorMessage(error, 'run')
      artifactError.value = ''
    } finally {
      if (!disposed && version === contextGeneration) restoring.value = false
    }
  }
  async function loadLibraries(page: number) {
    if (!canReadScope.value || page < 1 || page > 1000) return
    const generation = ++libraryGeneration
    libraryLoading.value = true
    libraryError.value = ''
    libraries.value = null
    try {
      const data = await call((signal) => assistantApi.libraries(page, signal))
      if (disposed || generation !== libraryGeneration) return
      libraries.value = data
    } catch (error) {
      if (!disposed && generation === libraryGeneration) libraryError.value = errorMessage(error)
    } finally {
      if (!disposed && generation === libraryGeneration) libraryLoading.value = false
    }
  }
  async function loadDocuments(page = 1) {
    const generation = ++documentGeneration
    documents.value = null
    documentError.value = ''
    if (!selectedLibraries.value.length) {
      documentLoading.value = false
      return
    }
    const ids = selectedLibraries.value.map((l) => l.libraryId)
    documentLoading.value = true
    try {
      const data = await call((signal) => assistantApi.documents(ids, page, signal))
      if (disposed || generation !== documentGeneration) return
      documents.value = data
    } catch (error) {
      if (!disposed && generation === documentGeneration) documentError.value = errorMessage(error)
    } finally {
      if (!disposed && generation === documentGeneration) documentLoading.value = false
    }
  }
  function toggleLibrary(item: LibraryOption) {
    if (selectionLocked.value || !item.selectable) return
    const existing = selectedLibraries.value.some((l) => l.libraryId === item.libraryId)
    if (!existing && selectedLibraries.value.length >= 1000) return
    selectedLibraries.value = existing
      ? selectedLibraries.value.filter((l) => l.libraryId !== item.libraryId)
      : [...selectedLibraries.value, item]
    selectedDocuments.value = []
    void loadDocuments()
  }
  function toggleDocument(item: DocumentOption) {
    if (selectionLocked.value || !item.selectable) return
    const existing = selectedDocuments.value.some((d) => d.documentId === item.documentId)
    if (!existing && selectedDocuments.value.length >= 2) return
    selectedDocuments.value = existing
      ? selectedDocuments.value.filter((d) => d.documentId !== item.documentId)
      : [...selectedDocuments.value, item]
  }
  function stopPoll() {
    if (pollTimer) clearTimeout(pollTimer)
    pollTimer = undefined
  }
  function acceptRun(data: Run) {
    run.value = data
    if (!selectedArtifactId.value) {
      const first = data.tasks.find((t) => t.artifactId)
      if (first?.artifactId) void loadArtifact(first.artifactId)
    }
    stopPoll()
    const version = contextGeneration
    if (['running', 'cancelling'].includes(data.status) && !disposed)
      pollTimer = setTimeout(() => {
        if (version === contextGeneration) void refreshRun()
      }, 1000)
  }
  async function startRun() {
    if (!canStart.value) return
    const version = contextGeneration
    stopPoll()
    creating.value = true
    runError.value = ''
    notice.value = ''
    runMissing.value = false
    run.value = null
    artifact.value = null
    selectedArtifactId.value = ''
    artifactError.value = ''
    ++artifactGeneration
    try {
      const data = await call((signal) =>
        assistantApi.create(
          {
            templateId: 'source_graph_citation_v1',
            selection: {
              libraryIds: selectedLibraries.value.map((l) => l.libraryId),
              documentIds: selectedDocuments.value.map((d) => d.documentId),
            },
            query: query.value.trim(),
            ...(goal.value.trim() ? { goal: goal.value.trim() } : {}),
          },
          signal,
        ),
      )
      if (!disposed && version === contextGeneration) {
        if (capabilities.value)
          assertRunIdentity(data, data.runId, capabilities.value.hostInstanceId)
        acceptRun(data)
      }
    } catch (error) {
      if (disposed || version !== contextGeneration) return
      runError.value = errorMessage(error)
      // 传输中断或无效回包无法证明创建未受理，禁止自动重发。
      submissionUncertain.value =
        !(error instanceof ApiError) || ['NETWORK_ERROR', 'BAD_RESPONSE'].includes(error.code)
      if (error instanceof ApiError && error.code === 'SELECTION_CHANGED') {
        selectedDocuments.value = []
        void loadDocuments()
      }
    } finally {
      if (!disposed && version === contextGeneration) creating.value = false
    }
  }
  async function refreshRun() {
    if (!run.value || polling.value || cancelling.value || runMissing.value) return
    stopPoll()
    polling.value = true
    runError.value = ''
    const id = run.value.runId,
      version = contextGeneration
    try {
      const data = await call((signal) => assistantApi.run(id, signal))
      if (!disposed && version === contextGeneration && run.value?.runId === id) {
        assertRunIdentity(data, id, run.value.hostInstanceId)
        acceptRun(data)
      }
    } catch (error) {
      if (!disposed && version === contextGeneration && run.value?.runId === id) {
        runError.value = errorMessage(error, 'run')
        runMissing.value =
          error instanceof ApiError && (error.status === 404 || error.status === 410)
      }
    } finally {
      if (!disposed && version === contextGeneration) polling.value = false
    }
  }
  async function cancelRun() {
    if (!run.value || !active.value || cancelling.value || polling.value) return
    stopPoll()
    cancelling.value = true
    runError.value = ''
    notice.value = ''
    const id = run.value.runId,
      version = contextGeneration
    try {
      const data = await call((signal) => assistantApi.cancel(id, signal))
      if (!disposed && version === contextGeneration && run.value?.runId === id) {
        assertRunIdentity(data.run, id, run.value.hostInstanceId)
        notice.value = data.accepted
          ? '服务端已接受取消，请等待最终状态；取消前已完成的成果保留。'
          : '本次取消未被接受，已更新当前运行状态。'
        acceptRun(data.run)
      }
    } catch (error) {
      if (!disposed && version === contextGeneration) runError.value = errorMessage(error, 'run')
    } finally {
      if (!disposed && version === contextGeneration) cancelling.value = false
    }
  }
  async function loadArtifact(id: string) {
    if (!run.value || runMissing.value) return false
    const runId = run.value.runId,
      generation = ++artifactGeneration,
      version = contextGeneration
    selectedArtifactId.value = id
    artifactLoading.value = true
    artifactError.value = ''
    artifact.value = null
    try {
      const data = await call((signal) => assistantApi.artifact(runId, id, signal))
      if (
        disposed ||
        version !== contextGeneration ||
        generation !== artifactGeneration ||
        run.value?.runId !== runId
      )
        return false
      if (
        data.runId !== runId ||
        data.artifactId !== id ||
        run.value.tasks.filter((t) => t.taskId === data.producerTaskId && t.artifactId === id)
          .length !== 1
      )
        throw new ApiError('BAD_RESPONSE', '成果身份与当前运行任务不一致。', 200)
      artifact.value = data
      return true
    } catch (error) {
      if (!disposed && version === contextGeneration && generation === artifactGeneration)
        artifactError.value = errorMessage(error, 'run')
      return false
    } finally {
      if (!disposed && version === contextGeneration && generation === artifactGeneration)
        artifactLoading.value = false
    }
  }
  onMounted(() => void restoreFromLocation())
  watch(
    [
      () => route.query.runId,
      () => route.query.artifactId,
      () => 'runId' in route.query,
      () => 'artifactId' in route.query,
    ],
    () => void restoreFromLocation(),
    { flush: 'sync' },
  )
  onBeforeUnmount(() => {
    disposed = true
    ++contextGeneration
    stopPoll()
    controllers.forEach((c) => c.abort())
  })
  return {
    returnTargeted,
    restoring,
    restoreError,
    restoreFromLocation,
    capabilities,
    template,
    serviceLoading,
    serviceError,
    libraries,
    documents,
    libraryLoading,
    documentLoading,
    libraryError,
    documentError,
    selectedLibraries,
    selectedDocuments,
    query,
    goal,
    run,
    creating,
    cancelling,
    polling,
    runError,
    notice,
    runMissing,
    submissionUncertain,
    artifact,
    artifactLoading,
    artifactError,
    selectedArtifactId,
    active,
    selectionLocked,
    canReadScope,
    canStart,
    loadService,
    loadLibraries,
    loadDocuments,
    toggleLibrary,
    toggleDocument,
    startRun,
    refreshRun,
    cancelRun,
    loadArtifact,
  }
}
