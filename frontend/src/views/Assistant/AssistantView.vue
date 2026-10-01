<template>
  <AdminLayout>
    <div class="assistant-workbench min-w-0">
      <PageBreadcrumb
        page-title="助手工作台"
        description="选定资料范围，按固定模板研究图谱、核查引用并重读原文。"
      />
      <section
        class="mb-6 rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]"
        aria-labelledby="service-title"
      >
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 id="service-title" class="text-base font-semibold text-gray-900 dark:text-white/90">
              服务能力
            </h2>
            <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
              {{
                returnTargeted
                  ? '只读恢复原运行与指定成果；可用性由当前宿主确认'
                  : '固定工具模板 · 未保存草稿刷新后清空，定位链接可重读未过期成果'
              }}
            </p>
          </div>
          <Button
            size="sm"
            variant="outline"
            :disabled="serviceLoading || selectionLocked"
            @click="loadService"
            >{{ serviceLoading ? '正在检查…' : '刷新服务状态' }}</Button
          >
        </div>
        <Alert
          v-if="serviceError"
          class="mt-4"
          variant="error"
          title="助手服务未就绪"
          :message="errorSummary(serviceError)"
        />
        <details v-if="serviceError" class="mt-2 text-xs">
          <summary class="cursor-pointer">服务错误详情</summary>
          <p>{{ serviceError }}</p>
        </details>
        <p
          v-if="serviceLoading"
          class="mt-4 text-sm text-gray-500 dark:text-gray-400"
          role="status"
        >
          正在读取宿主能力，不探测数据库或模型。
        </p>
        <template v-if="capabilities">
          <ul class="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <li
              v-for="capability in capabilities.capabilities"
              :key="capability.id"
              class="rounded-xl border border-gray-100 p-3 dark:border-gray-800"
            >
              <p class="text-sm font-medium text-gray-800 dark:text-gray-200">
                {{ capabilityLabels[capability.id] }} · {{ statusLabel(capability.status) }}
              </p>
              <p
                v-if="capability.reasonCodes.length"
                class="mt-1 break-words text-xs text-gray-500 dark:text-gray-400"
              >
                {{ capability.reasonCodes.map(reasonLabel).join('；') }}
              </p>
              <details class="mt-2 text-xs">
                <summary class="cursor-pointer">能力诊断详情</summary>
                <p>能力：{{ capability.id }}；状态：{{ capability.status }}</p>
                <p>原始原因：{{ capability.reasonCodes.join('；') || '无' }}</p>
              </details>
            </li>
          </ul>
          <p
            v-if="!capabilities.databaseIdentityVerified"
            class="mt-3 text-sm text-warning-700 dark:text-warning-400"
          >
            宿主尚未确认只读数据库身份，当前不能选资料或运行。
          </p>
          <details class="mt-3 break-words text-xs text-gray-500 dark:text-gray-400">
            <summary class="cursor-pointer">服务详情</summary>
            <p class="mt-2">
              宿主 {{ capabilities.hostInstanceId }}；观察时间
              {{ capabilities.observedAt }}；当前为隔离单 owner 模式，未提供多用户隔离。
            </p>
            <p>
              运行仅存内存，终态保留
              {{ capabilities.persistence.terminalRetentionSeconds }}
              秒；宿主重启或到期后不可恢复。刷新不会自动恢复无定位参数的草稿与运行。
            </p>
          </details>
        </template>
      </section>
      <section
        v-if="returnTargeted"
        class="mb-6 rounded-2xl border border-gray-200 bg-white p-5 text-sm text-gray-700 dark:border-gray-800 dark:bg-white/[0.03] dark:text-gray-300"
        aria-labelledby="restore-title"
      >
        <h2 id="restore-title" class="font-semibold text-gray-900 dark:text-white/90">
          返回原成果 · 只读恢复
        </h2>
        <p class="mt-2">根据定位重新读取服务端的原运行与指定成果；不会创建新任务或恢复本地聊天。</p>
        <p v-if="restoring" class="mt-3" role="status">正在恢复原成果…</p>
        <Alert
          v-if="restoreError"
          class="mt-3"
          variant="error"
          title="原成果恢复失败"
          :message="errorSummary(restoreError)"
          role="alert"
        />
        <details v-if="restoreError" class="mt-2 text-xs">
          <summary class="cursor-pointer">恢复错误详情</summary>
          <p>{{ restoreError }}</p>
        </details>
        <div class="mt-3 flex flex-wrap items-center gap-3">
          <Button size="sm" variant="outline" :disabled="restoring" @click="restoreFromLocation"
            >重新读取原定位</Button
          >
          <RouterLink
            to="/assistant"
            class="rounded text-brand-600 focus-visible:outline-2 focus-visible:outline-brand-500 dark:text-brand-400"
            >返回空白助手</RouterLink
          >
        </div>
      </section>
      <div class="mb-4 flex gap-2 lg:hidden" aria-label="工作台分区">
        <Button
          size="sm"
          :variant="mobileSection === 'setup' ? 'primary' : 'outline'"
          :aria-pressed="mobileSection === 'setup'"
          aria-controls="setup-panel"
          @click="mobileSection = 'setup'"
          >范围与任务</Button
        >
        <Button
          size="sm"
          :variant="mobileSection === 'results' ? 'primary' : 'outline'"
          :aria-pressed="mobileSection === 'results'"
          aria-controls="results-panel"
          @click="mobileSection = 'results'"
          >步骤与成果</Button
        >
      </div>
      <div class="grid items-start gap-6 lg:grid-cols-[minmax(280px,0.85fr)_minmax(0,1.4fr)]">
        <section
          id="setup-panel"
          :class="mobileSection === 'setup' ? 'block' : 'hidden lg:block'"
          class="min-w-0 space-y-5 rounded-2xl border border-gray-200 bg-white p-5 text-sm text-gray-700 dark:border-gray-800 dark:bg-white/[0.03] dark:text-gray-300"
          aria-labelledby="setup-title"
        >
          <h2 id="setup-title" class="text-lg font-semibold text-gray-900 dark:text-white/90">
            范围与任务
          </h2>
          <p v-if="returnTargeted" class="text-xs leading-6 text-gray-500 dark:text-gray-400">
            当前只读显示原任务信息，范围以运行的冻结范围为准。如需新任务，请使用“返回空白助手”。
          </p>
          <fieldset :disabled="selectionLocked || !canReadScope" class="min-w-0">
            <legend class="mb-3 font-medium">1. 选择知识库</legend>
            <p v-if="!canReadScope" class="text-gray-500 dark:text-gray-400">
              资料读取服务未启用，无法获取可选范围。
            </p>
            <p v-if="libraryLoading" role="status">正在加载知识库…</p>
            <Alert
              v-if="libraryError"
              variant="error"
              title="范围读取失败"
              :message="errorSummary(libraryError)"
            />
            <details v-if="libraryError" class="mt-2 text-xs">
              <summary class="cursor-pointer">知识库读取错误详情</summary>
              <p>{{ libraryError }}</p>
            </details>
            <Button
              v-if="libraryError"
              class="mt-2"
              size="sm"
              variant="outline"
              @click="loadLibraries(1)"
              >重试范围读取</Button
            >
            <template v-if="libraries">
              <p v-if="!libraries.items.length">
                {{
                  libraries.total === '0'
                    ? '当前授权范围没有知识库。'
                    : '本页无知识库，不能据此判断全范围为空。'
                }}
              </p>
              <label
                v-for="library in libraries.items"
                :key="library.libraryId"
                class="mb-2 flex items-start gap-3 rounded-lg border border-gray-100 p-3 dark:border-gray-800"
              >
                <input
                  type="checkbox"
                  class="mt-1 shrink-0 accent-brand-500"
                  :checked="selectedLibraries.some((item) => item.libraryId === library.libraryId)"
                  :disabled="
                    !library.selectable ||
                    (!selectedLibraries.some((item) => item.libraryId === library.libraryId) &&
                      selectedLibraries.length >= 1000)
                  "
                  @change="toggleLibrary(library)"
                />
                <span class="min-w-0"
                  ><span class="block break-words font-medium">{{ library.name }}</span
                  ><span class="text-xs text-gray-500 dark:text-gray-400"
                    >{{ statusLabel(library.status) }}{{ !library.selectable ? ' · 不可选' : '' }}
                    {{ library.reasonCodes.map(reasonLabel).join('；') }}</span
                  ></span
                >
              </label>
              <div class="mt-3 flex flex-wrap items-center gap-2">
                <Button
                  size="sm"
                  variant="ghost"
                  :disabled="libraries.page <= 1 || libraryLoading"
                  @click="loadLibraries(libraries.page - 1)"
                  >上一页</Button
                ><span class="text-xs"
                  >第 {{ libraries.page }} 页 · 共 {{ libraries.total }} 个</span
                ><Button
                  size="sm"
                  variant="ghost"
                  :disabled="!libraries.hasMore || libraries.page >= 1000 || libraryLoading"
                  @click="loadLibraries(libraries.page + 1)"
                  >下一页</Button
                >
              </div>
              <p
                v-if="!libraries.coverage.completeCoverage || libraries.coverage.truncated"
                class="mt-2 text-xs"
              >
                库列表覆盖不完整{{ libraries.coverage.truncated ? '，返回截断' : '' }}。
              </p>
              <p
                v-for="(reason, index) in libraries.coverage.limitations"
                :key="index"
                class="mt-1 break-words text-xs"
              >
                {{ reasonLabel(reason) }}
              </p>
              <details class="mt-2 text-xs">
                <summary class="cursor-pointer">知识库列表诊断详情</summary>
                <p v-for="library in libraries.items" :key="library.libraryId">
                  {{ library.libraryId }}；{{ library.status }}；{{
                    library.reasonCodes.join('；') || '无原因码'
                  }}
                </p>
                <p v-for="(reason, index) in libraries.coverage.limitations" :key="index">
                  {{ reason }}
                </p>
              </details>
            </template>
            <div v-if="selectedLibraries.length" class="mt-3">
              <p class="text-xs">
                已选 {{ selectedLibraries.length }} 个库（包含其他分页）；改变库选择会清空资料选择。
              </p>
              <ul class="mt-2 space-y-1">
                <li
                  v-for="item in selectedLibraries"
                  :key="item.libraryId"
                  class="flex items-center justify-between gap-2"
                >
                  <span class="min-w-0 break-words">{{ item.name }}</span
                  ><Button
                    size="sm"
                    variant="ghost"
                    :aria-label="'移除知识库 ' + item.name"
                    @click="toggleLibrary(item)"
                    >移除</Button
                  >
                </li>
              </ul>
            </div>
          </fieldset>
          <fieldset
            :disabled="selectionLocked || !canReadScope"
            class="min-w-0 border-t border-gray-100 pt-4 dark:border-gray-800"
          >
            <legend class="font-medium">2. 选择 1～2 份资料</legend>
            <p v-if="!selectedLibraries.length" class="mt-2 text-gray-500 dark:text-gray-400">
              先选择知识库，再读取允许的资料。
            </p>
            <p v-if="documentLoading" class="mt-2" role="status">正在加载所选库的资料…</p>
            <Alert
              v-if="documentError"
              class="mt-2"
              variant="error"
              title="资料读取失败"
              :message="errorSummary(documentError)"
            />
            <details v-if="documentError" class="mt-2 text-xs">
              <summary class="cursor-pointer">资料读取错误详情</summary>
              <p>{{ documentError }}</p>
            </details>
            <Button
              v-if="documentError"
              class="mt-2"
              size="sm"
              variant="outline"
              @click="loadDocuments()"
              >重试资料读取</Button
            >
            <template v-if="documents">
              <p v-if="!documents.items.length" class="mt-2">
                {{
                  documents.total === '0'
                    ? '所选范围没有资料。'
                    : '本页无资料，不能据此判断全范围为空。'
                }}
              </p>
              <label
                v-for="document in documents.items"
                :key="document.documentId"
                class="mt-2 flex items-start gap-3 rounded-lg border border-gray-100 p-3 dark:border-gray-800"
              >
                <input
                  type="checkbox"
                  class="mt-1 shrink-0 accent-brand-500"
                  :checked="
                    selectedDocuments.some((item) => item.documentId === document.documentId)
                  "
                  :disabled="
                    !document.selectable ||
                    (selectedDocuments.length >= 2 &&
                      !selectedDocuments.some((item) => item.documentId === document.documentId))
                  "
                  @change="toggleDocument(document)"
                />
                <span class="min-w-0"
                  ><span class="block break-words font-medium">{{
                    document.title || document.originalName
                  }}</span
                  ><span class="text-xs text-gray-500 dark:text-gray-400"
                    >{{ statusLabel(document.processingStatus) }} /
                    {{
                      document.lifecycleStatus
                        ? statusLabel(document.lifecycleStatus)
                        : '生命周期未提供'
                    }}{{ !document.selectable ? ' · 不可选' : '' }}
                    {{ document.reasonCodes.map(reasonLabel).join('；') }}</span
                  ></span
                >
              </label>
              <div class="mt-3 flex flex-wrap items-center gap-2">
                <Button
                  size="sm"
                  variant="ghost"
                  :disabled="documents.page <= 1 || documentLoading"
                  @click="loadDocuments(documents.page - 1)"
                  >上一页</Button
                ><span class="text-xs"
                  >第 {{ documents.page }} 页 · 共 {{ documents.total }} 份</span
                ><Button
                  size="sm"
                  variant="ghost"
                  :disabled="!documents.hasMore || documents.page >= 1000 || documentLoading"
                  @click="loadDocuments(documents.page + 1)"
                  >下一页</Button
                >
              </div>
              <p
                v-if="!documents.coverage.completeCoverage || documents.coverage.truncated"
                class="mt-2 text-xs"
              >
                资料列表覆盖不完整{{ documents.coverage.truncated ? '，返回截断' : '' }}。
              </p>
              <p
                v-for="(reason, index) in documents.coverage.limitations"
                :key="index"
                class="mt-1 break-words text-xs"
              >
                {{ reasonLabel(reason) }}
              </p>
              <p class="mt-2 text-xs">处理完成或处于启用状态不代表语义正确或人工核验。</p>
              <details class="mt-2 text-xs">
                <summary class="cursor-pointer">资料列表诊断详情</summary>
                <p v-for="document in documents.items" :key="document.documentId">
                  {{ document.documentId }}；{{ document.processingStatus }}；{{
                    document.lifecycleStatus
                  }}；{{ document.reasonCodes.join('；') || '无原因码' }}
                </p>
                <p v-for="(reason, index) in documents.coverage.limitations" :key="index">
                  {{ reason }}
                </p>
              </details>
            </template>
            <ul v-if="selectedDocuments.length" class="mt-3 space-y-1">
              <li
                v-for="item in selectedDocuments"
                :key="item.documentId"
                class="flex items-center justify-between gap-2"
              >
                <span class="min-w-0 break-words">已选：{{ item.title || item.originalName }}</span
                ><Button
                  size="sm"
                  variant="ghost"
                  :aria-label="'移除资料 ' + (item.title || item.originalName)"
                  @click="toggleDocument(item)"
                  >移除</Button
                >
              </li>
            </ul>
          </fieldset>
          <form
            class="space-y-4 border-t border-gray-100 pt-4 dark:border-gray-800"
            @submit.prevent="submit"
          >
            <div>
              <h3 class="font-medium">3. 任务模板</h3>
              <p class="mt-2">{{ template?.label || '资料读取 → 图谱研究 → 引用核查' }}</p>
              <p class="mt-1 text-xs">
                {{ template ? statusLabel(template.status) : '模板服务未接入' }}
                {{ template?.reasonCodes.map(reasonLabel).join('；') }}
              </p>
              <details v-if="template" class="mt-2 text-xs">
                <summary class="cursor-pointer">模板诊断详情</summary>
                <p>
                  {{ template.templateId }}；{{ template.status }}；{{
                    template.reasonCodes.join('；') || '无原因码'
                  }}
                </p>
              </details>
              <p class="mt-2 text-xs leading-5 text-gray-500 dark:text-gray-400">
                本次按固定模板执行；两份资料分别处理，不自动作比较裁决。
              </p>
            </div>
            <div>
              <label for="assistant-query" class="mb-2 block font-medium"
                >检索主题 <span class="text-xs font-normal">（2～200 字）</span></label
              ><input
                id="assistant-query"
                v-model="query"
                required
                minlength="2"
                maxlength="200"
                :disabled="selectionLocked"
                aria-describedby="assistant-query-hint"
                class="w-full rounded-lg border border-gray-300 bg-transparent px-3 py-2.5 text-gray-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-500/20 disabled:opacity-50 dark:border-gray-700 dark:text-white/90"
                placeholder="输入原文中实际出现的概念或短语"
              />
              <p
                id="assistant-query-hint"
                class="mt-2 text-xs leading-5 text-gray-500 dark:text-gray-400"
              >
                当前使用关键词匹配。建议输入原文中实际出现的概念或短语，整句指令不等于语义检索。
                <span
                  v-if="capabilities?.capabilities.find((item) => item.id === 'real_model')?.reasonCodes.includes('REAL_MODEL_NOT_CONNECTED')"
                >
                  当前宿主报告真实模型尚未接入，不会自动理解或改写检索主题。
                </span>
              </p>
            </div>
            <div>
              <label for="assistant-goal" class="mb-2 block font-medium"
                >目标说明 <span class="text-xs font-normal">（选填）</span></label
              ><textarea
                id="assistant-goal"
                v-model="goal"
                rows="3"
                maxlength="4000"
                :disabled="selectionLocked"
                class="w-full resize-y rounded-lg border border-gray-300 bg-transparent px-3 py-2.5 text-gray-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-500/20 disabled:opacity-50 dark:border-gray-700 dark:text-white/90"
                placeholder="说明你希望查看哪些成果"
              />
              <p class="mt-1 text-xs leading-5 text-gray-500 dark:text-gray-400">
                说明仅供显示，不改变执行计划；其中的上传、保存、评分等文字不会执行。
              </p>
            </div>
            <p class="text-xs text-gray-500 dark:text-gray-400">
              {{
                returnTargeted
                  ? '只读显示服务端原任务信息，不保存新草稿'
                  : '未保存草稿 · 页面刷新或关闭后清空，不保存聊天历史'
              }}
            </p>
            <Button type="submit" class="w-full" :disabled="!canStart">{{
              creating ? '正在提交…' : '发起只读研究'
            }}</Button>
            <p v-if="!canStart && !creating" class="text-xs text-gray-500 dark:text-gray-400">
              需服务能力和模板可用、选定库与 1～2 份资料、填写有效检索主题，且没有在途运行。
            </p>
          </form>
        </section>
        <section
          id="results-panel"
          :class="mobileSection === 'results' ? 'block' : 'hidden lg:block'"
          class="min-w-0 space-y-5 rounded-2xl border border-gray-200 bg-white p-5 text-sm text-gray-700 dark:border-gray-800 dark:bg-white/[0.03] dark:text-gray-300"
          aria-labelledby="results-title"
        >
          <div class="flex flex-wrap items-center justify-between gap-3">
            <h2 id="results-title" class="text-lg font-semibold text-gray-900 dark:text-white/90">
              步骤与成果
            </h2>
            <span v-if="run" role="status">{{
              runMissing ? '运行不可恢复' : statusLabel(run.status)
            }}</span
            ><span v-else class="text-xs text-gray-500 dark:text-gray-400">{{
              creating ? '等待服务受理' : '尚未发起'
            }}</span>
          </div>
          <Alert
            v-if="runError"
            variant="error"
            title="运行请求异常"
            :message="errorSummary(runError)"
          />
          <details v-if="runError" class="text-xs">
            <summary class="cursor-pointer">运行错误详情</summary>
            <p>{{ runError }}</p>
          </details>
          <p
            v-if="submissionUncertain"
            class="rounded-xl bg-warning-50 p-4 dark:bg-warning-500/10"
            role="alert"
          >
            创建请求的受理结果无法确认，已停止重发。请交总控核对，或等待宿主运行截止后刷新页面重新发起。
          </p>
          <p v-if="notice" role="status">{{ notice }}</p>
          <template v-if="run">
            <p class="break-words">检索主题：{{ run.query }}</p>
            <p v-if="run.goal" class="whitespace-pre-wrap break-words">目标说明：{{ run.goal }}</p>
            <div class="flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="outline"
                :disabled="polling || cancelling || runMissing"
                @click="refreshRun"
                >{{ polling ? '正在刷新…' : '刷新当前运行' }}</Button
              ><Button
                v-if="active"
                size="sm"
                variant="danger"
                :disabled="cancelling || polling || run.status === 'cancelling'"
                @click="cancelRun"
                >{{ cancelling || run.status === 'cancelling' ? '正在取消…' : '取消运行' }}</Button
              >
            </div>
            <p v-if="active" class="text-xs text-gray-500 dark:text-gray-400">
              {{
                runError
                  ? '轮询已暂停，请手动刷新当前运行；不会重新创建。'
                  : '每秒读取服务端快照，完成后停止轮询。'
              }}
            </p>
            <p v-if="run.status === 'cancelled'" class="text-xs">
              取消前已完成的成果可保留；在途任务不保证产出片段。
            </p>
            <ol class="space-y-3">
              <li
                v-for="(task, index) in run.tasks"
                :key="task.taskId"
                class="rounded-xl border border-gray-200 p-4 dark:border-gray-800"
              >
                <div class="flex flex-wrap items-center justify-between gap-2">
                  <h3 class="font-medium">{{ index + 1 }}. {{ stageLabels[task.stage] }}</h3>
                  <span>{{ statusLabel(task.status) }}</span>
                </div>
                <p class="mt-2 whitespace-pre-wrap break-words">
                  {{ stageSummary(task.objective) }}
                </p>
                <p
                  v-if="task.errorCode"
                  class="mt-2 break-words text-error-600 dark:text-error-400"
                >
                  原因：{{ reasonLabel(task.errorCode) }}
                </p>
                <Button
                  v-if="task.artifactId"
                  class="mt-3"
                  size="sm"
                  :variant="selectedArtifactId === task.artifactId ? 'primary' : 'outline'"
                  :disabled="runMissing"
                  @click="viewArtifact(task.artifactId)"
                  >{{ selectedArtifactId !== task.artifactId
                    ? '查看成果'
                    : artifactLoading
                      ? '正在读取 · 定位'
                      : artifactError
                        ? '读取失败 · 重试'
                        : artifact
                          ? '当前成果 · 定位'
                          : '已选 · 查看成果' }}</Button
                >
                <details class="mt-2 break-words text-xs text-gray-500 dark:text-gray-400">
                  <summary class="cursor-pointer">任务诊断、范围与依赖</summary>
                  <p>
                    原始阶段：{{ task.stage }}；目标：{{ task.objective }}；状态：{{
                      task.status
                    }}；错误：{{ task.errorCode || '无' }}
                  </p>
                  <p>
                    任务：{{ task.taskId }}；资料：{{ task.authorizedScope.documentIds.join('、') }}
                  </p>
                  <p>
                    依赖：{{ task.dependencyIds.join('、') || '无' }}；实际输入：{{
                      task.inputArtifactIds.join('、') || '无'
                    }}
                  </p>
                  <p>
                    工具调用 {{ task.usage.toolCalls }}；确定性决策
                    {{ task.usage.decisionCalls }}；模型调用 {{ task.usage.modelCalls }}
                  </p>
                </details>
              </li>
            </ol>
            <ul v-if="run.limitations.length" class="list-inside list-disc break-words text-xs">
              <li v-for="(reason, index) in run.limitations" :key="index">
                {{ reasonLabel(reason) }}
              </li>
            </ul>
            <details class="break-words text-xs text-gray-500 dark:text-gray-400">
              <summary class="cursor-pointer">运行与计数详情</summary>
              <p>原始状态：{{ run.status }}</p>
              <p v-for="(reason, index) in run.limitations" :key="index">原始原因：{{ reason }}</p>
              <p class="mt-2">
                运行：{{ run.runId }}；库：{{ run.authorizedScope.libraryIds.join('、') }}；资料：{{
                  run.authorizedScope.documentIds.join('、')
                }}
              </p>
              <p>
                工具调用 {{ run.usage.toolCalls }}；确定性决策
                {{ run.usage.decisionCalls }}；模型调用
                {{ run.usage.modelCalls }}
              </p>
              <p>
                创建 {{ run.createdAt }}；截止 {{ run.deadline }}；到期
                {{ run.expiresAt || '运行中尚未设置' }}；观察 {{ run.observedAt }}
              </p>
            </details>
          </template>
          <p
            v-else-if="!creating"
            class="py-10 text-center leading-7 text-gray-500 dark:text-gray-400"
          >
            选定资料并填写主题后，服务返回的步骤、成果和证据会显示在这里。<br />未接入的服务不会生成示例成果。
          </p>
          <div
            ref="artifactRegion"
            class="scroll-mt-24 border-t border-gray-100 pt-5 dark:border-gray-800 focus-visible:outline-2 focus-visible:outline-brand-500"
            tabindex="-1"
            role="region"
            aria-labelledby="artifact-title"
          >
            <h2 id="artifact-title" class="mb-4 text-base font-semibold text-gray-900 dark:text-white/90">
              当前成果
            </h2>
            <div v-if="selectedStep" class="mb-3" aria-live="polite">
              <p>当前选择：第 {{ selectedStep.index + 1 }} 步 · {{ stageLabels[selectedStep.task.stage] }}</p>
              <p class="mt-1 text-xs">
                资料 ID：{{ selectedStep.task.authorizedScope.documentIds.join('、') || '服务未提供资料范围' }}
              </p>
              <p v-if="artifact && !artifactLoading && !artifactError" class="mt-1 text-xs">已读取所选成果 · {{ statusLabel(artifact.outcome) }}</p>
            </div>
            <p v-if="artifactLoading" role="status">正在读取成果…</p>
            <Alert
              v-if="artifactError"
              variant="error"
              title="成果读取失败"
              :message="errorSummary(artifactError)"
            />
            <details v-if="artifactError" class="mt-2 text-xs">
              <summary class="cursor-pointer">成果错误详情</summary>
              <p>{{ artifactError }}</p>
            </details>
            <Button
              v-if="artifactError && !runMissing"
              class="mt-3"
              size="sm"
              variant="outline"
              @click="viewArtifact(selectedArtifactId)"
              >重试当前成果</Button
            ><ArtifactPanel
              v-if="artifact && !runMissing"
              :key="artifact.runId + ':' + artifact.artifactId"
              :artifact="artifact"
              :can-reread="
                capabilities?.capabilities.find((item) => item.id === 'source_reread')?.status ===
                'available'
              "
            />
            <p
              v-else-if="!artifactLoading && !artifactError"
              class="text-gray-500 dark:text-gray-400"
            >
              {{
                runMissing
                  ? '运行不可恢复，请重新发起。'
                  : '尚无可查看的成果。已失败或阻断的步骤会保留原因。'
              }}
            </p>
          </div>
        </section>
      </div>
    </div>
  </AdminLayout>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import PageBreadcrumb from '@/components/common/PageBreadcrumb.vue'
import Button from '@/components/ui/Button.vue'
import Alert from '@/components/ui/Alert.vue'
import ArtifactPanel from '@/features/assistant/ArtifactPanel.vue'
import {
  capabilityLabels,
  stageLabels,
  statusLabel,
  reasonLabel,
  stageSummary,
  errorSummary,
} from '@/features/assistant/presentation'
import { useAssistantWorkbench } from '@/features/assistant/useAssistantWorkbench'
const mobileSection = ref<'setup' | 'results'>('setup')
const {
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
} = useAssistantWorkbench()
const route = useRoute()
const artifactRegion = ref<HTMLElement | null>(null)
const selectedStep = computed(() => {
  const tasks = run.value?.tasks ?? []
  const index = tasks.findIndex((task) => task.artifactId === selectedArtifactId.value)
  return index < 0 ? null : { index, task: tasks[index]! }
})
let viewGeneration = 0
let viewDisposed = false
watch(
  [() => route.fullPath, () => run.value?.runId],
  () => { viewGeneration++ },
  { flush: 'sync' },
)
onBeforeUnmount(() => {
  viewDisposed = true
  viewGeneration++
})
async function viewArtifact(id: string) {
  const runId = run.value?.runId
  if (!runId || runMissing.value || !id) return
  const generation = ++viewGeneration
  mobileSection.value = 'results'
  void loadArtifact(id)
  await nextTick()
  if (
    viewDisposed || generation !== viewGeneration ||
    run.value?.runId !== runId || selectedArtifactId.value !== id
  ) return
  // 只在本次用户点击后定位；请求完成、自动预选和轮询均不触发滚动或焦点。
  artifactRegion.value?.focus({ preventScroll: true })
  artifactRegion.value?.scrollIntoView({ block: 'start', behavior: 'auto' })
}
watch(
  returnTargeted,
  (targeted) => {
    mobileSection.value = targeted ? 'results' : 'setup'
  },
  { immediate: true },
)
function submit() {
  if (!canStart.value) return
  mobileSection.value = 'results'
  void startRun()
}
</script>
<style scoped>
.assistant-workbench {
  overflow-wrap: anywhere;
}
.assistant-workbench :deep(details),
.assistant-workbench :deep(details p) {
  min-width: 0;
  overflow-wrap: anywhere;
}
.assistant-workbench :deep(summary:focus-visible) {
  outline: 2px solid var(--color-brand-500);
  outline-offset: 2px;
  border-radius: 0.25rem;
}
</style>
