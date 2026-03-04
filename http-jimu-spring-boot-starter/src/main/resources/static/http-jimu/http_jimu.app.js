        const app = createApp({
            setup() {
                const configs = ref([]);
                const dialogVisible = ref(false);
                const testVisible = ref(false);
                const testLoading = ref(false);
                const testResult = ref('');
                const testDetail = ref(null);
                const previewDetail = ref(null);
                const testParams = ref('{}');
                const testHttpId = ref('');
                const headerList = ref([]);
                const paramList = ref([]);
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
                
                // 积木库相关
                const stepLibraryVisible = ref(false);
                const stepEditVisible = ref(false);
                const librarySelectVisible = ref(false);
                const stepLibrary = ref([]);
                const stepForm = ref({ id: '', code: '', name: '', type: 'SCRIPT', target: 'BODY', description: '', configJson: '', scriptContent: '' });
                let stepScriptEditor = null;

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
                
                const form = ref({
                    id: '',
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
                    connectTimeout: null,
                    readTimeout: null,
                    writeTimeout: null,
                    callTimeout: null,
                    retryOnConnectionFailure: null,
                    followRedirects: null,
                    followSslRedirects: null,
                    dnsOverrides: '',
                    proxyHost: '',
                    proxyPort: null,
                    proxyType: 'HTTP'
                });

                // 连接池相关
                const poolDialogVisible = ref(false);
                const poolEditVisible = ref(false);
                const poolList = ref([]);
                const poolForm = ref({
                    id: '',
                    name: '',
                    maxIdleConnections: 5,
                    keepAliveDuration: 300000,
                    connectTimeout: 10000,
                    readTimeout: 10000,
                    writeTimeout: 10000,
                    callTimeout: 0,
                    retryOnConnectionFailure: true,
                    followRedirects: true,
                    followSslRedirects: true,
                    maxRequests: 64,
                    maxRequestsPerHost: 5,
                    pingInterval: 0,
                    dnsOverrides: '',
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

                const showPoolManagement = async () => {
                    await fetchPools();
                    poolDialogVisible.value = true;
                };

                const showPoolEdit = (row) => {
                    if (row) {
                        poolForm.value = { ...row };
                    } else {
                        poolForm.value = {
                            id: null,
                            name: '',
                            maxIdleConnections: 5,
                            keepAliveDuration: 300000,
                            connectTimeout: 10000,
                            readTimeout: 10000,
                            writeTimeout: 10000,
                            callTimeout: 0,
                            retryOnConnectionFailure: true,
                            followRedirects: true,
                            followSslRedirects: true,
                            maxRequests: 64,
                            maxRequestsPerHost: 5,
                            pingInterval: 0,
                            dnsOverrides: '',
                            proxyHost: '',
                            proxyPort: null,
                            proxyType: 'HTTP'
                        };
                    }
                    poolEditVisible.value = true;
                };

                const savePool = async () => {
                    if (!poolForm.value.name) {
                        ElementPlus.ElMessage.error('Pool Name is required');
                        return;
                    }
                    const res = await axios.post('http-jimu-api/pools/save', poolForm.value);
                    if (res.data.code === 1000) {
                        ElementPlus.ElMessage.success('保存成功');
                        poolEditVisible.value = false;
                        fetchPools();
                    }
                };

                const deletePool = async (row) => {
                    await ElementPlus.ElMessageBox.confirm('确删除该连接池吗？');
                    const res = await axios.delete(`http-jimu-api/pools/delete/${row.id}`);
                    if (res.data.code === 1000) {
                        ElementPlus.ElMessage.success('删除成功');
                        fetchPools();
                    }
                };

                const applyQuickCron = (val) => {
                    form.value.cronConfig = val;
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
                    const contextKeys = collectNonEmptyKeys(paramList.value);
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
                    if (row) {
                        form.value = {
                            ...row,
                            poolId: row.poolId || '',
                            connectTimeout: row.connectTimeout,
                            readTimeout: row.readTimeout,
                            writeTimeout: row.writeTimeout,
                            callTimeout: row.callTimeout,
                            retryOnConnectionFailure: row.retryOnConnectionFailure,
                            followRedirects: row.followRedirects,
                            followSslRedirects: row.followSslRedirects,
                            dnsOverrides: row.dnsOverrides || '',
                            proxyHost: row.proxyHost || '',
                            proxyPort: row.proxyPort,
                            proxyType: row.proxyType || 'HTTP'
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
                            paramList.value = Array.isArray(p) ? p : [];
                        } catch(e) { paramList.value = []; }
                        ensureEmptyRow(paramList.value);
                        
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
                            connectTimeout: null,
                            readTimeout: null,
                            writeTimeout: null,
                            callTimeout: null,
                            retryOnConnectionFailure: null,
                            followRedirects: null,
                            followSslRedirects: null,
                            dnsOverrides: '',
                            proxyHost: '',
                            proxyPort: null,
                            proxyType: 'HTTP'
                        };
                        steps.value = [];
                        quickCron.value = '';
                        headerList.value = [{key: 'Content-Type', value: 'application/json'}, {key: '', value: ''}];
                        paramList.value = [{key: '', value: ''}];
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
                    form.value.queryParams = JSON.stringify(paramList.value.filter(p => p.key));
                    
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
                    const res = await axios.post('http-jimu-api/save', form.value);
                    if (res.data.code === 1000) {
                        ElementPlus.ElMessage.success('保存成功');
                        dialogVisible.value = false;
                        fetchConfigs();
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
                    const res = await axios.post('http-jimu-api/steps/save', stepForm.value);
                    if (res.data.code === 1000) {
                        ElementPlus.ElMessage.success('保存成功');
                        stepEditVisible.value = false;
                        showStepLibrary(); // Refresh list
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
                    await fetchConfigs();
                });

                return {
                    configs, dialogVisible, form, steps, testVisible, testParams, testResult, testDetail, previewDetail, testLoading, headerList, paramList, bodyFormDataList, bodyUrlEncodedList, quickCron, activeTab, commonHeaders,
                    showEditDialog, addStep, saveConfig, deleteConfig, showTestDialog, runPreview, runTest, syncStepJson, validateScriptStep, handleMethodChange, handleBodyTypeChange, handleRawTypeChange, ensureEmptyRow, applyQuickCron, prettyJson, formatSnapshot,
                    scheduleVisible, scheduleForm, logVisible, logDetailVisible, jobLogs, currentConfigName, currentLog,
                    showScheduleDialog, saveSchedule, showLogDialog, viewLogDetail,
                    // Step Library
                    stepLibraryVisible, stepEditVisible, librarySelectVisible, stepLibrary, stepForm, showStepLibrary, showStepEditDialog, saveStepLibraryItem, deleteStep, addStepFromLibrary, selectLibraryStep,
                    handleStepDialogOpened,
                    // Pool
                    poolDialogVisible, poolEditVisible, poolList, poolForm, showPoolManagement, showPoolEdit, savePool, deletePool
                };
            }
        });
        app.use(ElementPlus);
        app.mount('#app');
