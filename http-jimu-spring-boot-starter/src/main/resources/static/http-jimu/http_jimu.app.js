(async () => {
    const templateFiles = [
        './http-jimu/templates/dashboard.html',
        './http-jimu/templates/group-dialog.html',
        './http-jimu/templates/pool-dialogs.html',
        './http-jimu/templates/step-library-dialog.html',
        './http-jimu/templates/step-edit-dialog.html',
        './http-jimu/templates/edit-dialog.html',
        './http-jimu/templates/publish-dialog.html',
        './http-jimu/templates/library-select-dialog.html',
        './http-jimu/templates/interceptor-editor-dialog.html',
        './http-jimu/templates/interceptor-library-dialog.html',
        './http-jimu/templates/advanced-config-dialog.html',
        './http-jimu/templates/test-dialog.html',
        './http-jimu/templates/schedule-dialog.html',
        './http-jimu/templates/log-dialogs.html'
    ];
    const templateResponses = await Promise.all(templateFiles.map((path) => fetch(path)));
    const failedTemplate = templateResponses.find((response) => !response.ok);
    if (failedTemplate) {
        document.getElementById('app').innerHTML = '<div style="padding: 24px; color: #f56c6c;">页面模板加载失败</div>';
        throw new Error('Failed to load template fragment: ' + failedTemplate.url);
    }
    const templateParts = await Promise.all(templateResponses.map((response) => response.text()));
    const appTemplate = '<div class="app-container">\n' + templateParts.join('\n') + '\n</div>';

    const app = createApp({
        template: appTemplate,
            setup() {
                const configs = ref([]);
                const dialogVisible = ref(false);
                const publishVisible = ref(false);
                const advancedConfigVisible = ref(false);
                const testVisible = ref(false);
                const testLoading = ref(false);
                const testResult = ref('');
                const testDetail = ref(null);
                const previewDetail = ref(null);
                const testParams = ref('{}');
                const testHttpId = ref('');
                const headerList = ref([]);
                const queryParamList = ref([]);
                const contextParamList = ref([]);
                const exposedMappingList = ref([]);
                const bodyFormDataList = ref([]);
                const bodyUrlEncodedList = ref([]);
                const quickCron = ref('');
                const activeTab = ref('params');
                
                // 定时任务相关
                const scheduleVisible = ref(false);
                const scheduleForm = ref({ id: '', enableJob: false, cronConfig: '' });
                const logVisible = ref(false);
                const logDetailVisible = ref(false);
                const jobLogs = ref([]);
                const currentConfigName = ref('');
                const currentLog = ref({});
                const selectedGroupKey = ref('all');
                const searchKeyword = ref('');
                
                // 积木库相关
                const stepLibraryVisible = ref(false);
                const stepEditVisible = ref(false);
                const librarySelectVisible = ref(false);
                const stepLibrary = ref([]);
                const stepForm = ref({ id: '', code: '', name: '', type: 'SCRIPT', target: 'BODY', description: '', configJson: '', scriptContent: '' });
                let stepScriptEditor = null;
                const interceptorEditorVisible = ref(false);
                const interceptorLibrarySelectVisible = ref(false);
                const interceptorEditorTitle = ref('');
                const interceptorSteps = ref([]);
                const interceptorTarget = ref('');
                const interceptorEditors = {};
                const groupDialogVisible = ref(false);
                const groupEditVisible = ref(false);
                const savedGroupList = ref([]);
                const groupList = ref([]);
                const groupForm = ref({ id: '', code: '', name: '', description: '', stepsConfig: '[]' });

                const commonHeaders = [
                    'Content-Type', 'Authorization', 'Accept', 'User-Agent', 
                    'Cache-Control', 'Host', 'Origin', 'Referer', 'Cookie'
                ];

                const rawTypeContentTypes = {
                    'text': 'text/plain',
                    'javascript': 'application/javascript',
                    'json': 'application/json',
                    'html': 'text/html',
                    'xml': 'application/xml'
                };
                const rawTypeEditorLanguages = {
                    text: 'plaintext',
                    javascript: 'plaintext',
                    json: 'json',
                    html: 'plaintext',
                    xml: 'plaintext'
                };
                const normalizeHttpMethod = (method, fallback = 'POST') => {
                    const value = (method || fallback || 'POST').toString().trim().toUpperCase();
                    return value || 'POST';
                };
                const resolveExposedMethodDefault = (row) => normalizeHttpMethod(
                    row && row.exposeApi && row.exposedMethod ? row.exposedMethod : (row && row.method ? row.method : 'POST')
                );
                
                const form = ref({
                    id: '',
                    groupId: '',
                    httpId: '',
                    name: '',
                    url: '',
                    method: 'POST',
                    headers: '[]',
                    queryParams: '[]',
                    bodyConfig: '',
                    bodyType: 'none',
                    bodyRawType: 'json',
                    cronConfig: '',
                    enableJob: false,
                    stepsConfig: '[]',
                    poolId: '',
                    connectTimeout: 10000,
                    readTimeout: 10000,
                    writeTimeout: 10000,
                    callTimeout: 0,
                    retryOnConnectionFailure: null,
                    followRedirects: null,
                    followSslRedirects: null,
                    retryMaxAttempts: 0,
                    retryOnHttpStatus: '',
                    proxyHost: '',
                    proxyPort: null,
                    proxyType: 'HTTP',
                    paramsConfig: '[]',
                    exposeApi: false,
                    exposedPath: '',
                    exposedMethod: 'POST',
                    exposedParamType: 'AUTO',
                    exposedMappingConfig: '[]'
                });
                const publishForm = ref({
                    id: '',
                    httpId: '',
                    name: '',
                    exposeApi: false,
                    exposedPath: '',
                    exposedMethod: 'POST',
                    exposedParamType: 'AUTO',
                    exposedMappingConfig: '[]',
                    paramsConfig: '[]'
                });

                // 连接池相关
                const poolDialogVisible = ref(false);
                const poolEditVisible = ref(false);
                const poolList = ref([]);
                const poolForm = ref({
                    id: '',
                    name: '',
                    stepsConfig: '[]',
                    maxIdleConnections: 5,
                    keepAliveDuration: 300000,
                    connectTimeout: 10000,
                    readTimeout: 10000,
                    writeTimeout: 10000,
                    callTimeout: 0,
                    retryOnConnectionFailure: true,
                    followRedirects: true,
                    followSslRedirects: true,
                    retryMaxAttempts: 0,
                    retryOnHttpStatus: '',
                    maxRequests: 64,
                    maxRequestsPerHost: 5,
                    pingInterval: 0,
                    proxyHost: '',
                    proxyPort: null,
                    proxyType: 'HTTP'
                });

                // 连接池
                const fetchPools = async () => {
                    const res = await axios.get('http-jimu-api/pools');
                    if (res.data.code === 1000) {
                        poolList.value = res.data.data;
                    }
                };

                const fetchGroups = async () => {
                    const res = await axios.get('http-jimu-api/groups');
                    if (res.data.code === 1000) {
                        savedGroupList.value = res.data.data || [];
                    }
                };

                const resolveGroupName = (groupId) => {
                    const found = savedGroupList.value.find((item) => item.id === groupId);
                    return found ? found.name : '-';
                };

                const groupTreeData = Vue.computed(() => {
                    const groupedCount = new Map();
                    let ungroupedCount = 0;
                    (configs.value || []).forEach((item) => {
                        if (item && item.groupId) {
                            groupedCount.set(item.groupId, (groupedCount.get(item.groupId) || 0) + 1);
                        } else {
                            ungroupedCount++;
                        }
                    });
                    return [{
                        id: 'all',
                        label: `全部接口 (${configs.value.length})`,
                        children: [
                            { id: '__ungrouped__', label: `未分组 (${ungroupedCount})` },
                            ...savedGroupList.value.filter((group) => !!group.id).map((group) => ({
                                id: group.id,
                                label: `${group.name} (${groupedCount.get(group.id) || 0})`
                            }))
                        ]
                    }];
                });

                const filteredConfigs = Vue.computed(() => {
                    const keyword = (searchKeyword.value || '').trim().toLowerCase();
                    return (configs.value || []).filter((item) => {
                        if (!item) return false;
                        const matchGroup = selectedGroupKey.value === 'all'
                            ? true
                            : selectedGroupKey.value === '__ungrouped__'
                                ? !item.groupId
                                : item.groupId === selectedGroupKey.value;
                        if (!matchGroup) {
                            return false;
                        }
                        if (!keyword) {
                            return true;
                        }
                        const haystack = [
                            item.httpId,
                            item.name,
                            item.url,
                            item.method,
                            resolveGroupName(item.groupId)
                        ].join(' ').toLowerCase();
                        return haystack.includes(keyword);
                    });
                });

                const handleGroupNodeClick = (node) => {
                    selectedGroupKey.value = node && node.id ? node.id : 'all';
                };

                const summarizeStepsConfig = (stepsConfig) => {
                    try {
                        const parsed = JSON.parse(stepsConfig || '[]');
                        return Array.isArray(parsed) ? `${parsed.length} 个` : '0 个';
                    } catch (e) {
                        return '格式错误';
                    }
                };

                const disposeInterceptorEditors = () => {
                    Object.keys(interceptorEditors).forEach((key) => {
                        if (interceptorEditors[key]) {
                            unbindEditorLsp(interceptorEditors[key]);
                            interceptorEditors[key].dispose();
                            delete interceptorEditors[key];
                        }
                    });
                };

                const fetchStepsForSharedUse = async () => {
                    const res = await axios.get('http-jimu-api/steps');
                    if (res.data.code === 1000) {
                        stepLibrary.value = res.data.data;
                    }
                };

                const initInterceptorEditor = (index, code) => {
                    setTimeout(async () => {
                        const container = document.getElementById('interceptor-editor-' + index);
                        if (container) {
                            const monaco = await initMonaco();
                            if (interceptorEditors[index]) {
                                unbindEditorLsp(interceptorEditors[index]);
                                interceptorEditors[index].dispose();
                            }
                            interceptorEditors[index] = monaco.editor.create(container, {
                                value: code || '// Example:\n// headers.put("X-Trace", "demo");\n// return headers;',
                                language: 'java',
                                theme: 'vs',
                                automaticLayout: true,
                                minimap: { enabled: false },
                                scrollBeyondLastLine: false,
                                fontSize: 14,
                                fixedOverflowWidgets: true,
                                renderControlCharacters: true
                            });
                            bindEditorLsp(interceptorEditors[index], `interceptor-step-${index}`);
                            setTimeout(() => interceptorEditors[index].layout(), 200);
                        }
                    }, 300);
                };

                const loadInterceptorSteps = (stepsConfig) => {
                    disposeInterceptorEditors();
                    let parsed = [];
                    try {
                        parsed = JSON.parse(stepsConfig || '[]');
                    } catch (e) {
                        parsed = [];
                    }
                    interceptorSteps.value = (Array.isArray(parsed) ? parsed : []).map((s, idx) => {
                        if (!s.config) s.config = {};
                        if (s.type === 'ADD_FIXED' || s.stepCode) s.config_json = JSON.stringify(s.config || {});
                        if (s.type === 'SCRIPT') initInterceptorEditor(idx, (s.config && s.config.script) ? s.config.script : '');
                        return s;
                    });
                };

                const showInterceptorEditor = async (target) => {
                    await fetchStepsForSharedUse();
                    interceptorTarget.value = target;
                    interceptorEditorTitle.value = target === 'group'
                        ? `拦截器（分组公共步骤） - ${groupForm.value.name || groupForm.value.code || '未命名分组'}`
                        : `拦截器（连接池公共步骤） - ${poolForm.value.name || '未命名连接池'}`;
                    loadInterceptorSteps(target === 'group' ? groupForm.value.stepsConfig : poolForm.value.stepsConfig);
                    interceptorEditorVisible.value = true;
                };

                const addInterceptorStep = (type) => {
                    const step = { type, config: {}, enableLog: true, target: 'BODY' };
                    const idx = interceptorSteps.value.length;
                    if (type === 'SIGN') {
                        step.config = { algorithm: 'MD5', targetField: 'sign', salt: '' };
                    } else if (type === 'ENCRYPT') {
                        step.config = { algorithm: 'HMAC_SHA256', fields: '', secret: '', iv: '', outputEncoding: 'BASE64', overwrite: true, targetField: '' };
                    } else if (type === 'ADD_FIXED') {
                        step.config_json = '{}';
                    } else if (type === 'SCRIPT') {
                        step.config = { script: '' };
                        initInterceptorEditor(idx, '');
                    }
                    interceptorSteps.value.push(step);
                };

                const syncInterceptorStepJson = (step) => {
                    try { step.config = JSON.parse(step.config_json); } catch (e) {}
                };

                const validateInterceptorScriptStep = async (index) => {
                    const editor = interceptorEditors[index];
                    const code = editor ? editor.getValue() : ((interceptorSteps.value[index] && interceptorSteps.value[index].config && interceptorSteps.value[index].config.script) || '');
                    try {
                        const res = await axios.post('http-jimu-api/validate-script', { script: code });
                        if (res.data.code !== 1000) {
                            ElementPlus.ElMessage.error(res.data.msg || '语法校验失败');
                            return;
                        }
                        const data = res.data.data || {};
                        if (editor && monacoInstance) {
                            monaco.editor.setModelMarkers(editor.getModel(), 'script-validate', []);
                        }
                        if (data.valid) {
                            ElementPlus.ElMessage.success('语法校验通过');
                            return;
                        }
                        const line = Number(data.line || 1);
                        const column = Number(data.column || 1);
                        if (editor && monacoInstance) {
                            const model = editor.getModel();
                            monaco.editor.setModelMarkers(model, 'script-validate', [{
                                startLineNumber: line,
                                startColumn: column,
                                endLineNumber: line,
                                endColumn: column + 1,
                                message: data.message || '语法错误',
                                severity: monaco.MarkerSeverity.Error
                            }]);
                            editor.revealLineInCenter(line);
                            editor.setPosition({ lineNumber: line, column });
                            editor.focus();
                        }
                        ElementPlus.ElMessage.error(`语法错误（第${line}行，第${column}列）：${data.message || ''}`);
                    } catch (e) {
                        ElementPlus.ElMessage.error('语法校验失败：' + (e.message || e));
                    }
                };

                const addInterceptorStepFromLibrary = async () => {
                    await fetchStepsForSharedUse();
                    interceptorLibrarySelectVisible.value = true;
                };

                const selectInterceptorLibraryStep = (row) => {
                    const step = {
                        type: row.type,
                        target: row.target,
                        stepCode: row.code,
                        config_json: row.configJson || '{}',
                        config: {},
                        enableLog: true
                    };
                    try { step.config = JSON.parse(step.config_json); } catch (e) {}
                    interceptorSteps.value.push(step);
                    interceptorLibrarySelectVisible.value = false;
                };

                const saveInterceptorEditor = () => {
                    interceptorSteps.value.forEach((s, idx) => {
                        if ((s.type === 'ADD_FIXED' || s.stepCode) && s.config_json) {
                            try { s.config = JSON.parse(s.config_json); } catch (e) {}
                        }
                        if (s.type === 'SCRIPT' && interceptorEditors[idx]) {
                            s.config.script = interceptorEditors[idx].getValue();
                        }
                    });
                    const json = JSON.stringify(interceptorSteps.value);
                    if (interceptorTarget.value === 'group') {
                        groupForm.value.stepsConfig = json;
                    } else if (interceptorTarget.value === 'pool') {
                        poolForm.value.stepsConfig = json;
                    }
                    interceptorEditorVisible.value = false;
                };

                const showGroupManagement = async () => {
                    await fetchGroups();
                    groupList.value = (savedGroupList.value || []).map((item) => ({ ...item }));
                    ensureGroupEmptyRow();
                    groupDialogVisible.value = true;
                };

                const ensureGroupEmptyRow = () => {
                    if (groupList.value.length === 0) {
                        groupList.value.push({ id: null, code: '', name: '', description: '', stepsConfig: '[]' });
                        return;
                    }
                    const last = groupList.value[groupList.value.length - 1];
                    if (last.code || last.name || last.description) {
                        groupList.value.push({ id: null, code: '', name: '', description: '', stepsConfig: '[]' });
                    }
                };

                const showGroupEdit = (row) => {
                    groupForm.value = row || { id: null, code: '', name: '', description: '', stepsConfig: '[]' };
                    showInterceptorEditor('group');
                };

                const saveGroup = async (row) => {
                    const target = row || groupForm.value;
                    if (!target.code) {
                        ElementPlus.ElMessage.error('分组编码不能为空');
                        return;
                    }
                    if (!target.name) {
                        ElementPlus.ElMessage.error('分组名称不能为空');
                        return;
                    }
                    try {
                        const res = await axios.post('http-jimu-api/groups/save', target);
                        if (res.data.code === 1000) {
                            ElementPlus.ElMessage.success('保存成功');
                            await fetchGroups();
                            groupList.value = (savedGroupList.value || []).map((item) => ({ ...item }));
                            ensureGroupEmptyRow();
                            await fetchConfigs();
                            return;
                        }
                        ElementPlus.ElMessage.error(res.data.msg || '保存失败');
                    } catch (e) {
                        const msg = e.response && e.response.data && e.response.data.msg
                            ? e.response.data.msg
                            : (e.message || '保存失败');
                        ElementPlus.ElMessage.error(msg);
                    }
                };

                const deleteGroup = async (row) => {
                    if (!row.id) {
                        groupList.value = groupList.value.filter((item) => item !== row);
                        ensureGroupEmptyRow();
                        return;
                    }
                    await ElementPlus.ElMessageBox.confirm('确定删除该分组吗？删除后接口会解除分组关联。');
                    const res = await axios.delete(`http-jimu-api/groups/delete/${row.id}`);
                    if (res.data.code === 1000) {
                        ElementPlus.ElMessage.success('删除成功');
                        await fetchGroups();
                        groupList.value = (savedGroupList.value || []).map((item) => ({ ...item }));
                        ensureGroupEmptyRow();
                        await fetchConfigs();
                    }
                };

                const showPoolManagement = async () => {
                    await fetchPools();
                    ensurePoolEmptyRow();
                    poolDialogVisible.value = true;
                };

                const ensurePoolEmptyRow = () => {
                    if (poolList.value.length === 0) {
                        poolList.value.push({
                            id: null,
                            name: '',
                            stepsConfig: '[]',
                            maxIdleConnections: 5,
                            keepAliveDuration: 300000,
                            connectTimeout: 10000,
                            readTimeout: 10000,
                            writeTimeout: 10000,
                            callTimeout: 0,
                            retryOnConnectionFailure: true,
                            followRedirects: true,
                            followSslRedirects: true,
                            retryMaxAttempts: 0,
                            retryOnHttpStatus: '',
                            maxRequests: 64,
                            maxRequestsPerHost: 5,
                            pingInterval: 0,
                            proxyHost: '',
                            proxyPort: null,
                            proxyType: 'HTTP'
                        });
                        return;
                    }
                    const last = poolList.value[poolList.value.length - 1];
                    if (last.name || last.proxyHost || last.retryOnHttpStatus) {
                        poolList.value.push({
                            id: null,
                            name: '',
                            stepsConfig: '[]',
                            maxIdleConnections: 5,
                            keepAliveDuration: 300000,
                            connectTimeout: 10000,
                            readTimeout: 10000,
                            writeTimeout: 10000,
                            callTimeout: 0,
                            retryOnConnectionFailure: true,
                            followRedirects: true,
                            followSslRedirects: true,
                            retryMaxAttempts: 0,
                            retryOnHttpStatus: '',
                            maxRequests: 64,
                            maxRequestsPerHost: 5,
                            pingInterval: 0,
                            proxyHost: '',
                            proxyPort: null,
                            proxyType: 'HTTP'
                        });
                    }
                };

                const showPoolEdit = (row) => {
                    poolForm.value = row;
                    showInterceptorEditor('pool');
                };

                const showPoolDetail = (row) => {
                    poolForm.value = row;
                    poolEditVisible.value = true;
                };

                const savePool = async (row) => {
                    const target = row || poolForm.value;
                    if (!target.name) {
                        ElementPlus.ElMessage.error('连接池名称不能为空');
                        return;
                    }
                    target.retryOnHttpStatus = (target.retryOnHttpStatus || '').trim();
                    if (!target.proxyHost) {
                        target.proxyPort = null;
                    }
                    try {
                        const res = await axios.post('http-jimu-api/pools/save', target);
                        if (res.data.code === 1000) {
                            ElementPlus.ElMessage.success('保存成功');
                            await fetchPools();
                            ensurePoolEmptyRow();
                            poolEditVisible.value = false;
                            return;
                        }
                        ElementPlus.ElMessage.error(res.data.msg || '保存失败');
                    } catch (e) {
                        const msg = e.response && e.response.data && e.response.data.msg
                            ? e.response.data.msg
                            : (e.message || '保存失败');
                        ElementPlus.ElMessage.error(msg);
                    }
                };

                const deletePool = async (row) => {
                    if (!row.id) {
                        poolList.value = poolList.value.filter((item) => item !== row);
                        ensurePoolEmptyRow();
                        return;
                    }
                    await ElementPlus.ElMessageBox.confirm('确删除该连接池吗？');
                    const res = await axios.delete(`http-jimu-api/pools/delete/${row.id}`);
                    if (res.data.code === 1000) {
                        ElementPlus.ElMessage.success('删除成功');
                        await fetchPools();
                        ensurePoolEmptyRow();
                    }
                };

                const applyQuickCron = (val) => {
                    form.value.cronConfig = val;
                };
                const showAdvancedConfigDialog = () => {
                    advancedConfigVisible.value = true;
                };
                const steps = ref([]);
                const editors = {};
                let bodyEditor = null;

                const collectNonEmptyKeys = (list) => {
                    const set = new Set();
                    (list || []).forEach((it) => {
                        const key = (it && it.key ? String(it.key) : '').trim();
                        if (key) set.add(key);
                    });
                    return Array.from(set);
                };

                const collectRawBodyKeys = () => {
                    if (!bodyEditor || form.value.bodyType !== 'raw') return [];
                    const raw = bodyEditor.getValue();
                    if (!raw || !raw.trim()) return [];
                    try {
                        const parsed = JSON.parse(raw);
                        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
                            return Object.keys(parsed).filter((k) => !!String(k || '').trim());
                        }
                    } catch (e) {
                        // ignore non-json raw body
                    }
                    return [];
                };

                dynamicScriptMetaSupplier = () => {
                    let contextKeys = [];
                    try {
                        const parsed = JSON.parse(form.value.paramsConfig || '[]');
                        if (Array.isArray(parsed)) {
                            contextKeys = collectNonEmptyKeys(parsed.map((item) => ({ key: item.key })));
                        }
                    } catch (e) {
                        contextKeys = [];
                    }
                    const headerKeys = collectNonEmptyKeys(headerList.value);
                    const formBodyKeys = form.value.bodyType === 'form-data'
                        ? collectNonEmptyKeys(bodyFormDataList.value)
                        : form.value.bodyType === 'x-www-form-urlencoded'
                            ? collectNonEmptyKeys(bodyUrlEncodedList.value)
                            : [];
                    const rawBodyKeys = collectRawBodyKeys();
                    const bodyKeys = Array.from(new Set([...formBodyKeys, ...rawBodyKeys]));
                    return {
                        contextKeys,
                        headerKeys,
                        queryKeys: contextKeys,
                        bodyKeys
                    };
                };

                const fetchConfigs = async () => {
                    const res = await axios.get('http-jimu-api/list');
                    if (res.data.code === 1000) configs.value = res.data.data;
                };

                const exportConfigs = async () => {
                    try {
                        const res = await axios.get('http-jimu-api/export');
                        if (res.data.code !== 1000) {
                            ElementPlus.ElMessage.error(res.data.msg || '导出失败');
                            return;
                        }
                        const blob = new Blob([JSON.stringify(res.data.data, null, 2)], { type: 'application/json;charset=utf-8' });
                        const link = document.createElement('a');
                        const url = URL.createObjectURL(blob);
                        link.href = url;
                        link.download = `http-jimu-export-${Date.now()}.json`;
                        document.body.appendChild(link);
                        link.click();
                        document.body.removeChild(link);
                        URL.revokeObjectURL(url);
                    } catch (e) {
                        ElementPlus.ElMessage.error('导出失败：' + (e.message || e));
                    }
                };

                const importConfigs = () => {
                    const input = document.createElement('input');
                    input.type = 'file';
                    input.accept = 'application/json,.json';
                    input.onchange = async (event) => {
                        const file = event.target.files && event.target.files[0];
                        if (!file) return;
                        try {
                            const text = await file.text();
                            const payload = JSON.parse(text);
                            const res = await axios.post('http-jimu-api/import', payload);
                            if (res.data.code === 1000) {
                                ElementPlus.ElMessage.success('导入成功');
                                await fetchGroups();
                                await fetchPools();
                                await fetchConfigs();
                                return;
                            }
                            ElementPlus.ElMessage.error(res.data.msg || '导入失败');
                        } catch (e) {
                            ElementPlus.ElMessage.error('导入失败：' + (e.message || e));
                        }
                    };
                    input.click();
                };
                const bindEditorLsp = () => {};
                const unbindEditorLsp = () => {};

                const initEditor = (index, code) => {
                    setTimeout(async () => {
                        const container = document.getElementById('editor-' + index);
                        if (container) {
                            const monaco = await initMonaco();
                            if (editors[index]) {
                                unbindEditorLsp(editors[index]);
                                editors[index].dispose();
                            }
                            editors[index] = monaco.editor.create(container, {
                                value: code || '// Example:\n// body.put("newField", "value");\n// return body;',
                                language: 'java',
                                theme: 'vs',
                                automaticLayout: true,
                                minimap: { enabled: false },
                                scrollBeyondLastLine: false,
                                fontSize: 14,
                                fixedOverflowWidgets: true,
                                renderControlCharacters: true
                            });
                            bindEditorLsp(editors[index], `flow-step-${index}`);
                            setTimeout(() => editors[index].layout(), 200);
                        }
                    }, 300);
                };

                const initBodyEditor = (code, lang = 'json') => {
                    setTimeout(async () => {
                        const container = document.getElementById('body-editor');
                        if (container) {
                            const monaco = await initMonaco();
                            const editorLang = rawTypeEditorLanguages[lang] || 'plaintext';
                            if (bodyEditor) bodyEditor.dispose();
                            bodyEditor = monaco.editor.create(container, {
                                value: code || (lang === 'json' ? '{}' : ''),
                                language: editorLang,
                                theme: 'vs',
                                automaticLayout: true,
                                minimap: { enabled: false },
                                scrollBeyondLastLine: false,
                                fontSize: 14
                            });
                            setTimeout(() => bodyEditor.layout(), 200);
                        }
                    }, 300);
                };

                // 监听标页切换
                watch(activeTab, (val) => {
                    if (val === 'steps') {
                        setTimeout(() => {
                            Object.values(editors).forEach(editor => {
                                editor.layout();
                                editor.focus();
                            });
                        }, 300);
                    } else if (val === 'body') {
                        setTimeout(() => bodyEditor && bodyEditor.layout(), 300);
                    }
                });

	                const ensureEmptyRow = (list) => {
	                    if (list.length === 0 || list[list.length - 1].key) {
	                        list.push({ key: '', value: '' });
	                    }
	                };

	                const ensureContextParamEmptyRow = () => {
	                    if (contextParamList.value.length === 0) {
	                        contextParamList.value.push({ key: '', source: '', defaultValue: '' });
	                        return;
	                    }
	                    const last = contextParamList.value[contextParamList.value.length - 1];
	                    if (last.key || last.source || last.defaultValue) {
	                        contextParamList.value.push({ key: '', source: '', defaultValue: '' });
	                    }
	                };

	                const ensureExposedMappingEmptyRow = () => {
	                    if (exposedMappingList.value.length === 0) {
	                        exposedMappingList.value.push({ sourceType: 'QUERY', targetType: 'BODY' });
	                        return;
	                    }
	                    const last = exposedMappingList.value[exposedMappingList.value.length - 1];
	                    if (last.sourceType || last.targetType) {
	                        exposedMappingList.value.push({ sourceType: 'QUERY', targetType: 'BODY' });
	                    }
	                };

                const availableMappingTargetTypes = (sourceType) => {
                    if (sourceType === 'RAW') {
                        return ['RAW'];
                    }
                    return ['PATH', 'QUERY', 'BODY', 'HEADER', 'FORM'];
                };

                const handleMappingSourceTypeChange = (row) => {
                    const options = availableMappingTargetTypes(row.sourceType);
                    if (!options.includes(row.targetType)) {
                        row.targetType = options[0];
                    }
                    ensureExposedMappingEmptyRow();
                };

                const handleMethodChange = (method) => {
                    const contentTypeIdx = headerList.value.findIndex(h => h.key && h.key.toLowerCase() === 'content-type');
                    if (['POST', 'PUT', 'PATCH'].includes(method)) {
                        if (contentTypeIdx === -1) {
                            if (form.value.bodyType === 'none') {
                                form.value.bodyType = 'raw';
                                form.value.bodyRawType = 'json';
                                updateContentTypeHeader('application/json');
                            }
                        }
                    } else if (['GET', 'DELETE', 'HEAD', 'OPTIONS'].includes(method)) {
                        form.value.bodyType = 'none';
                        if (contentTypeIdx !== -1 && headerList.value[contentTypeIdx].value === 'application/json') {
                            headerList.value.splice(contentTypeIdx, 1);
                            ensureEmptyRow(headerList.value);
                        }
                    }
                };

                const updateContentTypeHeader = (value) => {
                    const idx = headerList.value.findIndex(h => h.key && h.key.toLowerCase() === 'content-type');
                    if (idx !== -1) {
                        headerList.value[idx].value = value;
                    } else {
                      
                        const insertPos = headerList.value.length > 0 ? headerList.value.length - 1 : 0;
                        headerList.value.splice(insertPos, 0, { key: 'Content-Type', value: value });
                    }
                    ensureEmptyRow(headerList.value);
                };

                const handleBodyTypeChange = (type) => {
                    if (type === 'none') {
                        const idx = headerList.value.findIndex(h => h.key && h.key.toLowerCase() === 'content-type');
                        if (idx !== -1) {
                            headerList.value.splice(idx, 1);
                            ensureEmptyRow(headerList.value);
                        }
                    } else if (type === 'form-data') {
                        updateContentTypeHeader('multipart/form-data');
                        ensureEmptyRow(bodyFormDataList.value);
                    } else if (type === 'x-www-form-urlencoded') {
                        updateContentTypeHeader('application/x-www-form-urlencoded');
                        ensureEmptyRow(bodyUrlEncodedList.value);
                    } else if (type === 'raw') {
                        updateContentTypeHeader(rawTypeContentTypes[form.value.bodyRawType]);
                        initBodyEditor(bodyEditor ? bodyEditor.getValue() : '', form.value.bodyRawType);
                    }
                };

                const handleRawTypeChange = (rawType) => {
                    updateContentTypeHeader(rawTypeContentTypes[rawType]);
                    initBodyEditor(bodyEditor ? bodyEditor.getValue() : '', rawType);
                };

                const showEditDialog = async (row) => {
                    activeTab.value = 'params';
                    await fetchPools();
                    await fetchGroups();
                    if (row) {
                        form.value = {
                            ...row,
                            groupId: row.groupId || '',
                            poolId: row.poolId || '',
                            connectTimeout: row.connectTimeout,
                            readTimeout: row.readTimeout,
                            writeTimeout: row.writeTimeout,
                            callTimeout: row.callTimeout,
                            retryOnConnectionFailure: row.retryOnConnectionFailure,
                            followRedirects: row.followRedirects,
                            followSslRedirects: row.followSslRedirects,
                            retryMaxAttempts: row.retryMaxAttempts ?? 0,
                            retryOnHttpStatus: row.retryOnHttpStatus || '',
                            proxyHost: row.proxyHost || '',
                            proxyPort: row.proxyPort,
                            proxyType: row.proxyType || 'HTTP',
                            paramsConfig: row.paramsConfig || '[]',
                            exposeApi: !!row.exposeApi,
                            exposedPath: row.exposedPath || '',
                            exposedMethod: resolveExposedMethodDefault(row),
                            exposedParamType: row.exposedParamType || 'AUTO',
                            exposedMappingConfig: row.exposedMappingConfig || '[]'
                        };
                        // Headers
                        try {
                            const h = JSON.parse(row.headers || '[]');
                            headerList.value = Array.isArray(h) ? h : [];
                        } catch(e) { headerList.value = []; }
                        ensureEmptyRow(headerList.value);

                        // Params
                        try {
                            const p = JSON.parse(row.queryParams || '[]');
                            queryParamList.value = Array.isArray(p) ? p : [];
                        } catch(e) { queryParamList.value = []; }
                        ensureEmptyRow(queryParamList.value);
                        
                        // Body
                        if (row.bodyType === 'form-data') {
                            try { bodyFormDataList.value = JSON.parse(row.bodyConfig || '[]'); } catch(e) { bodyFormDataList.value = []; }
                            ensureEmptyRow(bodyFormDataList.value);
                        } else if (row.bodyType === 'x-www-form-urlencoded') {
                            try { bodyUrlEncodedList.value = JSON.parse(row.bodyConfig || '[]'); } catch(e) { bodyUrlEncodedList.value = []; }
                            ensureEmptyRow(bodyUrlEncodedList.value);
                        } else {
                            initBodyEditor(row.bodyConfig, row.bodyRawType || 'json');
                        }
                        
                        quickCron.value = row.cronConfig || '';
                        
                        // Steps
                        steps.value = JSON.parse(row.stepsConfig || '[]').map((s, idx) => {
                            if (!s.config) s.config = {};
                            if (s.type === 'ADD_FIXED') s.config_json = JSON.stringify(s.config || {});
                            if (s.stepCode) s.config_json = JSON.stringify(s.config || {});
                            if (s.type === 'SCRIPT') initEditor(idx, (s.config && s.config.script) ? s.config.script : '');
                            return s;
                        });
                    } else {
                        form.value = {
                            id: null,
                            groupId: '',
                            httpId: '',
                            name: '',
                            url: '',
                            method: 'POST',
                            headers: '[]',
                            queryParams: '[]',
                            bodyConfig: '',
                            bodyType: 'raw',
                            bodyRawType: 'json',
                            cronConfig: '',
                            enableJob: false,
                            stepsConfig: '[]',
                            poolId: '',
                            connectTimeout: 10000,
                            readTimeout: 10000,
                            writeTimeout: 10000,
                            callTimeout: 0,
                            retryOnConnectionFailure: null,
                            followRedirects: null,
                            followSslRedirects: null,
                            retryMaxAttempts: 0,
                            retryOnHttpStatus: '',
                            proxyHost: '',
                            proxyPort: null,
                            proxyType: 'HTTP',
                            paramsConfig: '[]',
                            exposeApi: false,
                            exposedPath: '',
                            exposedMethod: normalizeHttpMethod('POST'),
                            exposedParamType: 'AUTO',
                            exposedMappingConfig: '[]'
                        };
                        steps.value = [];
                        quickCron.value = '';
                        headerList.value = [{key: 'Content-Type', value: 'application/json'}, {key: '', value: ''}];
                        queryParamList.value = [{key: '', value: ''}];
                        bodyFormDataList.value = [{key: '', value: ''}];
                        bodyUrlEncodedList.value = [{key: '', value: ''}];
                        initBodyEditor('{}', 'json');
                    }
                    dialogVisible.value = true;
                };

                const addStep = (type) => {
                    const step = { type, config: {}, enableLog: true };
                    const idx = steps.value.length;
                    if (type === 'SIGN') {
                        step.config = { algorithm: 'MD5', targetField: 'sign', salt: '' };
                    } else if (type === 'ENCRYPT') {
                        step.config = { algorithm: 'HMAC_SHA256', fields: '', secret: '', iv: '', outputEncoding: 'BASE64', overwrite: true, targetField: '' };
                    } else if (type === 'ADD_FIXED') {
                        step.config_json = '{}';
                    } else if (type === 'SCRIPT') {
                        step.config = { script: '' };
                        initEditor(idx, '');
                    }
                    steps.value.push(step);
                };

                const syncStepJson = (step) => {
                    try { step.config = JSON.parse(step.config_json); } catch(e) {}
                };

                const validateScriptStep = async (index) => {
                    const editor = editors[index];
                    const code = editor ? editor.getValue() : ((steps.value[index] && steps.value[index].config && steps.value[index].config.script) || '');
                    try {
                        const res = await axios.post('http-jimu-api/validate-script', { script: code });
                        if (res.data.code !== 1000) {
                            ElementPlus.ElMessage.error(res.data.msg || '语法校验失败');
                            return;
                        }
                        const data = res.data.data || {};
                        if (editor && monacoInstance) {
                            monaco.editor.setModelMarkers(editor.getModel(), 'script-validate', []);
                        }
                        if (data.valid) {
                            ElementPlus.ElMessage.success('语法校验通过');
                            return;
                        }
                        const line = Number(data.line || 1);
                        const column = Number(data.column || 1);
                        if (editor && monacoInstance) {
                            const model = editor.getModel();
                            monaco.editor.setModelMarkers(model, 'script-validate', [{
                                startLineNumber: line,
                                startColumn: column,
                                endLineNumber: line,
                                endColumn: column + 1,
                                message: data.message || '语法错误',
                                severity: monaco.MarkerSeverity.Error
                            }]);
                            editor.revealLineInCenter(line);
                            editor.setPosition({ lineNumber: line, column });
                            editor.focus();
                        }
                        ElementPlus.ElMessage.error(`语法错误（第${line}行，第${column}列）：${data.message || ''}`);
                    } catch (e) {
                        ElementPlus.ElMessage.error('语法校验失败：' + (e.message || e));
                    }
                };

                const saveConfig = async () => {
                    if (!form.value.httpId) {
                        ElementPlus.ElMessage.error('HTTP ID is required');
                        return;
                    }
                    if (!form.value.url) {
                        ElementPlus.ElMessage.error('URL is required');
                        return;
                    }
                    form.value.headers = JSON.stringify(headerList.value.filter(h => h.key));
                    form.value.queryParams = JSON.stringify(queryParamList.value.filter(p => p.key));
                    
                    if (form.value.bodyType === 'form-data') {
                        form.value.bodyConfig = JSON.stringify(bodyFormDataList.value.filter(b => b.key));
                    } else if (form.value.bodyType === 'x-www-form-urlencoded') {
                        form.value.bodyConfig = JSON.stringify(bodyUrlEncodedList.value.filter(b => b.key));
                    } else if (form.value.bodyType === 'raw') {
                        if (bodyEditor) form.value.bodyConfig = bodyEditor.getValue();
                    } else {
                        form.value.bodyConfig = '';
                    }
                    
                    steps.value.forEach((s, idx) => {
                        if ((s.type === 'ADD_FIXED' || s.stepCode) && s.config_json) {
                            try { s.config = JSON.parse(s.config_json); } catch(e) {}
                        }
                        if (s.type === 'SCRIPT' && editors[idx]) s.config.script = editors[idx].getValue();
                    });
                    
                    form.value.stepsConfig = JSON.stringify(steps.value);
                    form.value.retryOnHttpStatus = (form.value.retryOnHttpStatus || '').trim();
                    if (!form.value.proxyHost) {
                        form.value.proxyPort = null;
                    }
                    if (!form.value.exposeApi) {
                        form.value.exposedPath = '';
                        form.value.exposedMethod = normalizeHttpMethod(form.value.method);
                        form.value.exposedParamType = 'AUTO';
                        form.value.exposedMappingConfig = '[]';
                    }
                    try {
                        const res = await axios.post('http-jimu-api/save', form.value);
                        if (res.data.code === 1000) {
                            ElementPlus.ElMessage.success('保存成功');
                            dialogVisible.value = false;
                            fetchConfigs();
                            return;
                        }
                        ElementPlus.ElMessage.error(res.data.msg || '保存失败');
                    } catch (e) {
                        const msg = e.response && e.response.data && e.response.data.msg
                            ? e.response.data.msg
                            : (e.message || '保存失败');
                        ElementPlus.ElMessage.error(msg);
                    }
                };

                const showPublishDialog = (row) => {
                    publishForm.value = {
                        ...row,
                        exposeApi: true,
                        exposedPath: row.exposedPath || '',
                        exposedMethod: resolveExposedMethodDefault(row),
                        exposedParamType: row.exposedParamType || 'AUTO',
                        exposedMappingConfig: row.exposedMappingConfig || '[]',
                        paramsConfig: row.paramsConfig || '[]'
                    };
                    try {
                        const pc = JSON.parse(row.paramsConfig || '[]');
                        contextParamList.value = Array.isArray(pc)
                            ? pc.map((item) => ({
                                key: item.key || '',
                                source: item.source || '',
                                defaultValue: item.defaultValue || ''
                            }))
                            : [];
                    } catch (e) { contextParamList.value = []; }
                    ensureContextParamEmptyRow();
                    try {
                        const mc = JSON.parse(row.exposedMappingConfig || '[]');
                        exposedMappingList.value = Array.isArray(mc)
                            ? mc.map((item) => ({
                                sourceType: item.sourceType || 'QUERY',
                                targetType: item.targetType || 'BODY'
                            }))
                            : [];
                    } catch (e) { exposedMappingList.value = []; }
                    ensureExposedMappingEmptyRow();
                    publishVisible.value = true;
                };

                const savePublishConfig = async () => {
                    publishForm.value.paramsConfig = JSON.stringify(contextParamList.value
                        .filter(p => p.key)
                        .map((item) => ({
                            key: item.key,
                            source: item.source,
                            defaultValue: item.defaultValue
                        })));
                    if (publishForm.value.exposedParamType === 'CUSTOM') {
                        publishForm.value.exposedMappingConfig = JSON.stringify(exposedMappingList.value
                            .filter(item => item.sourceType && item.targetType)
                            .map((item) => ({
                                sourceType: item.sourceType,
                                targetType: item.targetType
                            })));
                    } else {
                        publishForm.value.exposedMappingConfig = '[]';
                    }
                    try {
                        const res = await axios.post('http-jimu-api/save', publishForm.value);
                        if (res.data.code === 1000) {
                            ElementPlus.ElMessage.success('发布配置已更新');
                            publishVisible.value = false;
                            fetchConfigs();
                            return;
                        }
                        ElementPlus.ElMessage.error(res.data.msg || '保存失败');
                    } catch (e) {
                        const msg = e.response && e.response.data && e.response.data.msg
                            ? e.response.data.msg
                            : (e.message || '保存失败');
                        ElementPlus.ElMessage.error(msg);
                    }
                };

                const deleteConfig = async (row) => {
                    await ElementPlus.ElMessageBox.confirm('确定删除吗？');
                    const res = await axios.delete(`http-jimu-api/delete/${row.id}`);
                    if (res.data.code === 1000) {
                        ElementPlus.ElMessage.success('删除成功');
                        fetchConfigs();
                    }
                };

                const showTestDialog = (row) => {
                    testHttpId.value = row.httpId;
                    testResult.value = '';
                    testDetail.value = null;
                    previewDetail.value = null;
                    testVisible.value = true;
                };

                const prettyJson = (obj) => {
                    try {
                        if (obj === null || obj === undefined) return '{}';
                        if (typeof obj === 'string') return obj;
                        return JSON.stringify(obj, null, 2);
                    } catch (e) {
                        return String(obj);
                    }
                };

                const formatSnapshot = (text) => {
                    if (text === null || text === undefined || text === '') return '（空）';
                    try {
                        const parsed = JSON.parse(text);
                        return JSON.stringify(parsed, null, 2);
                    } catch (e) {
                        return text;
                    }
                };

                const runPreview = async () => {
                    testLoading.value = true;
                    try {
                        const params = JSON.parse(testParams.value);
                        const res = await axios.post(`http-jimu-api/preview-call/${testHttpId.value}`, params);
                        if (res.data.code === 1000) {
                            previewDetail.value = res.data.data;
                            testResult.value = '';
                        } else {
                            previewDetail.value = null;
                            testResult.value = res.data.msg || '预览失败';
                        }
                    } catch (e) {
                        previewDetail.value = null;
                        testResult.value = '预览失败：' + e.message;
                    } finally {
                        testLoading.value = false;
                    }
                };

                const runTest = async () => {
                    testLoading.value = true;
                    try {
                        const params = JSON.parse(testParams.value);
                        const res = await axios.post(`http-jimu-api/test-call/${testHttpId.value}`, params);
                        if (res.data.code === 1000) {
                            testDetail.value = res.data.data;
                            testResult.value = '';
                        } else {
                            testDetail.value = null;
                            testResult.value = res.data.msg || 'Call failed';
                        }
                    } catch (e) {
                        testDetail.value = null;
                        testResult.value = 'Error: ' + e.message;
                    } finally {
                        testLoading.value = false;
                    }
                };

                // 定时任务逻辑
                const showScheduleDialog = (row) => {
                    scheduleForm.value = { 
                        id: row.id, 
                        enableJob: row.enableJob, 
                        cronConfig: row.cronConfig,
                        // 包含有必要字段以防保存时丢失
                        ...row 
                    };
                    quickCron.value = row.cronConfig || '';
                    scheduleVisible.value = true;
                };

                const saveSchedule = async () => {
                    const res = await axios.post('http-jimu-api/save', scheduleForm.value);
                    if (res.data.code === 1000) {
                        ElementPlus.ElMessage.success('定时任务配置已更新');
                        scheduleVisible.value = false;
                        fetchConfigs();
                    }
                };

                const showLogDialog = async (row) => {
                    currentConfigName.value = row.name || row.httpId;
                    const res = await axios.get(`http-jimu-api/job-logs/${row.id}`);
                    if (res.data.code === 1000) {
                        jobLogs.value = res.data.data;
                        logVisible.value = true;
                    }
                };

                const viewLogDetail = (log) => {
                    currentLog.value = log;
                    logDetailVisible.value = true;
                };

                
                const showStepLibrary = async () => {
                    const res = await axios.get('http-jimu-api/steps');
                    if (res.data.code === 1000) {
                        stepLibrary.value = res.data.data;
                        stepLibraryVisible.value = true;
                    }
                };

                const showStepEditDialog = (row) => {
                    if (row) {
                        stepForm.value = { ...row, configJson: row.configJson || '', scriptContent: row.scriptContent || '' };
                    } else {
                        stepForm.value = { id: null, code: '', name: '', type: 'SCRIPT', target: 'BODY', description: '', configJson: '', scriptContent: '' };
                    }
                    stepEditVisible.value = true;
                };

                const handleStepDialogOpened = () => {
                    if (stepForm.value.type === 'SCRIPT') {
                        initStepScriptEditor(stepForm.value.scriptContent);
                    }
                };

                const initStepScriptEditor = (code) => {
                    nextTick(() => {
                        const container = document.getElementById('step-script-editor');
                        if (container) {
                            initMonaco().then(monaco => {
                                if (stepScriptEditor) {
                                    unbindEditorLsp(stepScriptEditor);
                                    stepScriptEditor.dispose();
                                }
                                stepScriptEditor = monaco.editor.create(container, {
                                    value: code || '',
                                    language: 'java',
                                    theme: 'vs',
                                    automaticLayout: true,
                                    minimap: { enabled: false }
                                });
                                bindEditorLsp(stepScriptEditor, 'library-step-editor');
                                // Force layout refresh after a short delay
                                setTimeout(() => {
                                    if (stepScriptEditor) {
                                        stepScriptEditor.layout();
                                    }
                                }, 100);
                            });
                        }
                    });
                };

                const saveStepLibraryItem = async () => {
                    if (stepForm.value.type === 'SCRIPT' && stepScriptEditor) {
                        stepForm.value.scriptContent = stepScriptEditor.getValue();
                    }
                    try {
                        const res = await axios.post('http-jimu-api/steps/save', stepForm.value);
                        if (res.data.code === 1000) {
                            ElementPlus.ElMessage.success('保存成功');
                            stepEditVisible.value = false;
                            showStepLibrary(); // Refresh list
                            return;
                        }
                        ElementPlus.ElMessage.error(res.data.msg || '保存失败');
                    } catch (e) {
                        const msg = e.response && e.response.data && e.response.data.msg
                            ? e.response.data.msg
                            : (e.message || '保存失败');
                        ElementPlus.ElMessage.error(msg);
                    }
                };

                const deleteStep = async (row) => {
                    await ElementPlus.ElMessageBox.confirm('确定删除该积木步骤吗？');
                    const res = await axios.delete(`http-jimu-api/steps/delete/${row.id}`);
                    if (res.data.code === 1000) {
                        ElementPlus.ElMessage.success('删除成功');
                        showStepLibrary();
                    }
                };

                const addStepFromLibrary = async () => {
                    const res = await axios.get('http-jimu-api/steps');
                    if (res.data.code === 1000) {
                        stepLibrary.value = res.data.data;
                        librarySelectVisible.value = true;
                    }
                };

                const selectLibraryStep = (row) => {
                    const step = { 
                        type: row.type, 
                        target: row.target,
                        stepCode: row.code,
                        config_json: row.configJson || '{}',
                        config: {},
                        enableLog: true 
                    };
                    try { step.config = JSON.parse(step.config_json); } catch(e) {}
                    steps.value.push(step);
                    librarySelectVisible.value = false;
                };

                watch(() => stepForm.value.type, (val) => {
                    if (val === 'SCRIPT' && stepEditVisible.value) {
                        setTimeout(() => initStepScriptEditor(stepForm.value.scriptContent), 100);
                    }
                });

                onMounted(async () => {
                    await loadScriptMeta();
                    await fetchGroups();
                    await fetchPools();
                    await fetchConfigs();
                });

                return {
                    configs, filteredConfigs, groupTreeData, selectedGroupKey, searchKeyword, handleGroupNodeClick, dialogVisible, publishVisible, publishForm, advancedConfigVisible, form, steps, testVisible, testParams, testResult, testDetail, previewDetail, testLoading, headerList, queryParamList, contextParamList, exposedMappingList, bodyFormDataList, bodyUrlEncodedList, quickCron, activeTab, commonHeaders,
                    groupDialogVisible, groupEditVisible, savedGroupList, groupList, groupForm, showGroupManagement, showGroupEdit, saveGroup, deleteGroup, ensureGroupEmptyRow, resolveGroupName, summarizeStepsConfig, exportConfigs, importConfigs,
                    interceptorEditorVisible, interceptorLibrarySelectVisible, interceptorEditorTitle, interceptorSteps, showInterceptorEditor, addInterceptorStep, syncInterceptorStepJson, validateInterceptorScriptStep, addInterceptorStepFromLibrary, selectInterceptorLibraryStep, saveInterceptorEditor, disposeInterceptorEditors,
                    showEditDialog, showPublishDialog, addStep, saveConfig, savePublishConfig, deleteConfig, showTestDialog, runPreview, runTest, syncStepJson, validateScriptStep, handleMethodChange, handleBodyTypeChange, handleRawTypeChange, ensureEmptyRow, ensureContextParamEmptyRow, ensureExposedMappingEmptyRow, availableMappingTargetTypes, handleMappingSourceTypeChange, applyQuickCron, prettyJson, formatSnapshot,
                    showAdvancedConfigDialog,
                    scheduleVisible, scheduleForm, logVisible, logDetailVisible, jobLogs, currentConfigName, currentLog,
                    showScheduleDialog, saveSchedule, showLogDialog, viewLogDetail,
                    // Step Library
                    stepLibraryVisible, stepEditVisible, librarySelectVisible, stepLibrary, stepForm, showStepLibrary, showStepEditDialog, saveStepLibraryItem, deleteStep, addStepFromLibrary, selectLibraryStep,
                    handleStepDialogOpened,
                    // Pool
                    poolDialogVisible, poolEditVisible, poolList, poolForm, showPoolManagement, showPoolEdit, showPoolDetail, savePool, deletePool, ensurePoolEmptyRow
                };
            }
        });
    app.use(ElementPlus);
    app.mount('#app');
})();



