const { createApp, ref, onMounted, nextTick, watch } = Vue;
        
        // Monaco Editor 
        let monacoInstance = null;
        let monacoProvidersReady = false;
        let scriptMetaLoaded = false;
        let dynamicScriptMetaSupplier = null;
        const beanMethodCache = {};
        let scriptMetaCache = {
            variables: [
                { name: 'body', type: 'java.lang.Object', description: '当前处理对象（请求/响应体）' },
                { name: 'context', type: 'java.util.Map<String,Object>', description: '上下文参数（call 传入 map）' },
                { name: 'url', type: 'java.lang.String', description: '请求 URL' },
                { name: 'headers', type: 'java.util.Map<String,String>', description: '请求头' },
                { name: 'queryParams', type: 'java.util.Map<String,String>', description: '查询参数' },
                { name: 'log', type: 'org.slf4j.Logger', description: '日志对象' },
                { name: 'redis', type: 'org.springframework.data.redis.core.StringRedisTemplate', description: 'Redis 模板（可用时注入）' }
            ],
            functions: [
                { name: 'return body;', insertText: 'return body;', description: '返回处理结果', parameters: ['body'], returnType: 'java.lang.Object' },
                { name: 'body.put(key, value)', insertText: 'body.put(${1:key}, ${2:value})', description: '向 Map 体中写字段', parameters: ['key', 'value'], returnType: 'java.lang.Object' },
                { name: 'context.get(key)', insertText: 'context.get(${1:key})', description: '从上下文读取参数', parameters: ['key'], returnType: 'java.lang.Object' },
                { name: 'log.info(msg)', insertText: 'log.info(${1:msg})', description: '输出日志', parameters: ['msg'], returnType: 'void' }
            ],
            members: {
                body: [],
                context: [],
                headers: [],
                queryParams: [],
                url: [],
                log: [],
                redis: []
            },
            classes: [
                { className: 'java.util.Map', description: 'Map 接口' },
                { className: 'java.util.HashMap', description: 'HashMap 实现' },
                { className: 'java.util.List', description: 'List 接口' },
                { className: 'java.util.ArrayList', description: 'ArrayList 实现' },
                { className: 'java.lang.String', description: '字符串' },
                { className: 'com.alibaba.fastjson2.JSON', description: 'fastjson2 JSON 工具类' },
                { className: 'com.alibaba.fastjson2.JSONObject', description: 'fastjson2 JSONObject' },
                { className: 'com.alibaba.fastjson2.JSONArray', description: 'fastjson2 JSONArray' }
            ],
            beans: []
        };

        const getDynamicScriptMeta = () => {
            try {
                if (typeof dynamicScriptMetaSupplier === 'function') {
                    const data = dynamicScriptMetaSupplier();
                    return data || {};
                }
            } catch (e) {
                console.warn('读取动态补全上下文失败', e);
            }
            return {};
        };

        const loadScriptMeta = async (force = false) => {
            if (scriptMetaLoaded && !force) return scriptMetaCache;
            try {
                const res = await axios.get('http-jimu-api/script-meta');
                if (res && res.data && res.data.code === 1000 && res.data.data) {
                    const remote = res.data.data;
                    scriptMetaCache = {
                        variables: Array.isArray(remote.variables) ? remote.variables : scriptMetaCache.variables,
                        functions: Array.isArray(remote.functions) ? remote.functions : scriptMetaCache.functions,
                        members: remote.members || scriptMetaCache.members,
                        classes: Array.isArray(remote.classes) ? remote.classes : scriptMetaCache.classes,
                        beans: Array.isArray(remote.beans) ? remote.beans : scriptMetaCache.beans
                    };
                }
            } catch (e) {
                console.warn('加载脚本补全元数据失败，使用本地默认元数据', e);
            } finally {
                scriptMetaLoaded = true;
            }
            return scriptMetaCache;
        };

        const fetchBeanMethods = async (beanName) => {
            const key = String(beanName || '').trim();
            if (!key) return [];
            if (beanMethodCache[key]) return beanMethodCache[key];
            try {
                const res = await axios.get(`http-jimu-api/script-meta/bean/${encodeURIComponent(key)}`);
                if (res && res.data && res.data.code === 1000 && res.data.data) {
                    const methods = Array.isArray(res.data.data.methods) ? res.data.data.methods : [];
                    beanMethodCache[key] = methods;
                    return methods;
                }
            } catch (e) {
                console.warn('加载 Bean 方法提示失败', key, e);
            }
            beanMethodCache[key] = [];
            return [];
        };

        const extractCallableName = (item) => {
            if (!item) return '';
            const text = item.insertText || item.name || '';
            const match = text.match(/^([A-Za-z_][A-Za-z0-9_]*)\s*\(/);
            if (match) return match[1];
            return '';
        };

        const registerScriptProviders = (monaco) => {
            if (monacoProvidersReady) return;
            monacoProvidersReady = true;
            loadScriptMeta();

            const scriptKeywords = ['if', 'else', 'for', 'while', 'try', 'catch', 'return', 'new', 'true', 'false', 'null'];
            const scriptSnippets = [
                { label: 'if', insertText: 'if (${1:condition}) {\n\t${2}\n}', detail: 'if 语句' },
                { label: 'ifelse', insertText: 'if (${1:condition}) {\n\t${2}\n} else {\n\t${3}\n}', detail: 'if else 语句' },
                { label: 'for', insertText: 'for (def ${1:item} : ${2:collection}) {\n\t${3}\n}', detail: 'for-each 语句' },
                { label: 'trycatch', insertText: 'try {\n\t${1}\n} catch (Exception ${2:e}) {\n\tlog.error(${3:\"脚本异常\"}, ${2:e});\n}', detail: 'try-catch 语句' }
            ];

            monaco.languages.registerCompletionItemProvider('java', {
                triggerCharacters: ['.', '('],
                provideCompletionItems: async (model, position) => {
                    const meta = await loadScriptMeta();
                    const dynamicMeta = getDynamicScriptMeta();
                    const word = model.getWordUntilPosition(position);
                    const range = {
                        startLineNumber: position.lineNumber,
                        endLineNumber: position.lineNumber,
                        startColumn: word.startColumn,
                        endColumn: word.endColumn
                    };
                    const linePrefix = model.getValueInRange({
                        startLineNumber: position.lineNumber,
                        startColumn: 1,
                        endLineNumber: position.lineNumber,
                        endColumn: position.column
                    });

                    const suggestions = [];
                    const beanArgMatch = linePrefix.match(/bean\s*\(\s*["']([^"']*)$/);
                    if (beanArgMatch) {
                        const beanPrefix = (beanArgMatch[1] || '').toLowerCase();
                        (meta.beans || []).forEach((b) => {
                            const beanName = String(b.name || '');
                            if (!beanName) return;
                            if (beanPrefix && !beanName.toLowerCase().startsWith(beanPrefix)) return;
                            suggestions.push({
                                label: beanName,
                                kind: monaco.languages.CompletionItemKind.Reference,
                                detail: b.type || 'Spring Bean',
                                documentation: `Spring Bean: ${beanName}`,
                                insertText: beanName,
                                range
                            });
                        });
                        return { suggestions };
                    }

                    const beanMemberMatch = linePrefix.match(/bean\s*\(\s*["']([^"']+)["']\s*\)\.([A-Za-z0-9_]*)$/);
                    if (beanMemberMatch) {
                        const beanName = beanMemberMatch[1];
                        const memberPrefix = beanMemberMatch[2] || '';
                        const methods = await fetchBeanMethods(beanName);
                        methods.forEach((m) => {
                            const name = m.name || '';
                            if (!name) return;
                            if (memberPrefix && !name.toLowerCase().startsWith(memberPrefix.toLowerCase())) return;
                            suggestions.push({
                                label: name,
                                kind: monaco.languages.CompletionItemKind.Method,
                                detail: m.description || m.returnType || '',
                                documentation: (m.parameters || []).join(', '),
                                insertText: m.insertText || `${name}($1)`,
                                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                                range
                            });
                        });
                        return { suggestions };
                    }

                    const memberMatch = linePrefix.match(/([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z0-9_]*)$/);
                    if (memberMatch) {
                        const owner = memberMatch[1];
                        const memberPrefix = memberMatch[2] || '';
                        const members = (meta.members && meta.members[owner]) || [];
                        members.forEach((m) => {
                            if (memberPrefix && !(m.name || '').toLowerCase().startsWith(memberPrefix.toLowerCase())) return;
                            suggestions.push({
                                label: m.name,
                                kind: monaco.languages.CompletionItemKind.Method,
                                detail: m.description || m.returnType || '',
                                documentation: (m.parameters || []).join(', '),
                                insertText: m.insertText || `${m.name}($1)`,
                                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                                range
                            });
                        });

                        const dynamicKeysMap = {
                            context: dynamicMeta.contextKeys || [],
                            body: dynamicMeta.bodyKeys || [],
                            headers: dynamicMeta.headerKeys || [],
                            queryParams: dynamicMeta.queryKeys || []
                        };
                        const dynamicKeys = dynamicKeysMap[owner] || [];
                        dynamicKeys.forEach((k) => {
                            const safe = String(k || '').trim();
                            if (!safe) return;
                            const matchable = safe.toLowerCase();
                            if (memberPrefix && !('get'.startsWith(memberPrefix.toLowerCase()) || matchable.startsWith(memberPrefix.toLowerCase()))) return;
                            suggestions.push({
                                label: `get("${safe}")`,
                                kind: monaco.languages.CompletionItemKind.Property,
                                detail: `${owner} 动态键`,
                                documentation: `来自当前配置的 ${owner} 字段：${safe}`,
                                insertText: `get("${safe}")`,
                                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                                range
                            });
                            if (owner === 'body' || owner === 'context') {
                                suggestions.push({
                                    label: `put("${safe}", value)`,
                                    kind: monaco.languages.CompletionItemKind.Method,
                                    detail: `${owner} 动态写入`,
                                    documentation: `写入当前配置字段：${safe}`,
                                    insertText: `put("${safe}", \${1:value})`,
                                    insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                                    range
                                });
                            }
                        });
                        return { suggestions };
                    }

                    (meta.variables || []).forEach((v) => {
                        suggestions.push({
                            label: v.name,
                            kind: monaco.languages.CompletionItemKind.Variable,
                            detail: v.type || '',
                            documentation: v.description || '',
                            insertText: v.name,
                            range
                        });
                    });

                    (meta.functions || []).forEach((f) => {
                        suggestions.push({
                            label: f.name,
                            kind: monaco.languages.CompletionItemKind.Function,
                            detail: (f.returnType || '') + (f.description ? ` | ${f.description}` : ''),
                            documentation: (f.parameters || []).join(', '),
                            insertText: f.insertText || f.name,
                            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                            range
                        });
                    });

                    (meta.beans || []).slice(0, 200).forEach((b) => {
                        const beanName = String((b && b.name) || '');
                        if (!beanName) return;
                        suggestions.push({
                            label: `bean("${beanName}")`,
                            kind: monaco.languages.CompletionItemKind.Reference,
                            detail: b.type || 'Spring Bean',
                            documentation: `Spring Bean: ${beanName}`,
                            insertText: `bean("${beanName}")`,
                            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                            range
                        });
                    });

                    (dynamicMeta.contextKeys || []).forEach((k) => {
                        const safe = String(k || '').trim();
                        if (!safe) return;
                        suggestions.push({
                            label: `context.get("${safe}")`,
                            kind: monaco.languages.CompletionItemKind.Function,
                            detail: '动态上下文键',
                            documentation: `来自当前参数配置：${safe}`,
                            insertText: `context.get("${safe}")`,
                            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                            range
                        });
                    });
                    (dynamicMeta.bodyKeys || []).forEach((k) => {
                        const safe = String(k || '').trim();
                        if (!safe) return;
                        suggestions.push({
                            label: `body.get("${safe}")`,
                            kind: monaco.languages.CompletionItemKind.Function,
                            detail: '动态 Body 键',
                            documentation: `来自当前请求体配置：${safe}`,
                            insertText: `body.get("${safe}")`,
                            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                            range
                        });
                    });

                    (meta.classes || []).forEach((c) => {
                        suggestions.push({
                            label: c.className,
                            kind: monaco.languages.CompletionItemKind.Class,
                            detail: c.description || '',
                            insertText: c.className,
                            range
                        });
                    });

                    scriptKeywords.forEach((k) => {
                        suggestions.push({
                            label: k,
                            kind: monaco.languages.CompletionItemKind.Keyword,
                            insertText: k,
                            range
                        });
                    });

                    scriptSnippets.forEach((s) => {
                        suggestions.push({
                            label: s.label,
                            kind: monaco.languages.CompletionItemKind.Snippet,
                            detail: s.detail,
                            insertText: s.insertText,
                            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                            range
                        });
                    });

                    return { suggestions };
                }
            });

            monaco.languages.registerHoverProvider('java', {
                provideHover: async (model, position) => {
                    const meta = await loadScriptMeta();
                    const dynamicMeta = getDynamicScriptMeta();
                    const word = model.getWordAtPosition(position);
                    if (!word || !word.word) return null;
                    const token = word.word;

                    const variable = (meta.variables || []).find(v => v.name === token);
                    if (variable) {
                        return {
                            contents: [
                                { value: `**${variable.name}**` },
                                { value: `类型: \`${variable.type || 'Object'}\`` },
                                { value: variable.description || '' }
                            ]
                        };
                    }

                    const fn = (meta.functions || []).find(f => (f.name || '').startsWith(token + '(') || extractCallableName(f) === token);
                    if (fn) {
                        return {
                            contents: [
                                { value: `**${fn.name || token}**` },
                                { value: `参数: ${(fn.parameters || []).join(', ') || '无'}` },
                                { value: `返回: \`${fn.returnType || 'Object'}\`` },
                                { value: fn.description || '' }
                            ]
                        };
                    }

                    const cls = (meta.classes || []).find(c => c.className === token);
                    if (cls) {
                        return {
                            contents: [
                                { value: `**${cls.className}**` },
                                { value: cls.description || '' }
                            ]
                        };
                    }

                    const beanMeta = (meta.beans || []).find(b => b.name === token);
                    if (beanMeta) {
                        return {
                            contents: [
                                { value: `**Bean: ${beanMeta.name}**` },
                                { value: `类型: \`${beanMeta.type || 'unknown'}\`` },
                                { value: `可调用: \`bean("${beanMeta.name}")\`` }
                            ]
                        };
                    }

                    const inContext = (dynamicMeta.contextKeys || []).includes(token);
                    const inBody = (dynamicMeta.bodyKeys || []).includes(token);
                    const inHeader = (dynamicMeta.headerKeys || []).includes(token);
                    const inQuery = (dynamicMeta.queryKeys || []).includes(token);
                    if (inContext || inBody || inHeader || inQuery) {
                        const sources = [];
                        if (inContext) sources.push('参数（context）');
                        if (inBody) sources.push('请求体（body）');
                        if (inHeader) sources.push('请求头（headers）');
                        if (inQuery) sources.push('查询参数（queryParams）');
                        return {
                            contents: [
                                { value: `**${token}**` },
                                { value: `动态字段来源: ${sources.join(' / ')}` }
                            ]
                        };
                    }
                    return null;
                }
            });

            monaco.languages.registerSignatureHelpProvider('java', {
                signatureHelpTriggerCharacters: ['(', ','],
                signatureHelpRetriggerCharacters: [','],
                provideSignatureHelp: async (model, position) => {
                    const meta = await loadScriptMeta();
                    const linePrefix = model.getValueInRange({
                        startLineNumber: position.lineNumber,
                        startColumn: 1,
                        endLineNumber: position.lineNumber,
                        endColumn: position.column
                    });
                    const beanCallMatch = linePrefix.match(/bean\s*\(\s*["']([^"']+)["']\s*\)\.([A-Za-z_][A-Za-z0-9_]*)\(([^()]*)$/);
                    if (beanCallMatch) {
                        const beanName = beanCallMatch[1];
                        const fnName = beanCallMatch[2];
                        const argText = beanCallMatch[3] || '';
                        const activeParameter = argText.trim() ? argText.split(',').length - 1 : 0;
                        const methods = await fetchBeanMethods(beanName);
                        const method = methods.find((m) => (m.name || '') === fnName);
                        if (!method) {
                            return { value: null, dispose: () => {} };
                        }
                        const params = (method.parameters || []).map((p) => ({ label: p }));
                        return {
                            value: {
                                signatures: [{
                                    label: `bean("${beanName}").${fnName}(${(method.parameters || []).join(', ')})`,
                                    documentation: method.description || method.returnType || '',
                                    parameters: params
                                }],
                                activeSignature: 0,
                                activeParameter: Math.min(activeParameter, Math.max(params.length - 1, 0))
                            },
                            dispose: () => {}
                        };
                    }
                    const callMatch = linePrefix.match(/(?:([A-Za-z_][A-Za-z0-9_]*)\.)?([A-Za-z_][A-Za-z0-9_]*)\(([^()]*)$/);
                    if (!callMatch) {
                        return { value: null, dispose: () => {} };
                    }
                    const owner = callMatch[1] || '';
                    const fnName = callMatch[2];
                    const argText = callMatch[3] || '';
                    const activeParameter = argText.trim() ? argText.split(',').length - 1 : 0;

                    let signatureLabel = fnName;
                    let description = '';
                    let params = [];
                    if (owner && meta.members && meta.members[owner]) {
                        const member = (meta.members[owner] || []).find(m => m.name === fnName);
                        if (!member) return { value: null, dispose: () => {} };
                        signatureLabel = `${owner}.${fnName}(${(member.parameters || []).join(', ')})`;
                        description = member.description || '';
                        params = (member.parameters || []).map((p) => ({ label: p }));
                    } else {
                        const targetFn = (meta.functions || []).find(f => extractCallableName(f) === fnName || (f.name || '').startsWith(fnName + '('));
                        if (!targetFn) {
                            return { value: null, dispose: () => {} };
                        }
                        signatureLabel = targetFn.name || fnName;
                        description = targetFn.description || '';
                        params = (targetFn.parameters || []).map((p) => ({ label: p }));
                    }

                    return {
                        value: {
                            signatures: [{
                                label: signatureLabel,
                                documentation: description,
                                parameters: params
                            }],
                            activeSignature: 0,
                            activeParameter: Math.min(activeParameter, Math.max(params.length - 1, 0))
                        },
                        dispose: () => {}
                    };
                }
            });
        };

        const initMonaco = () => {
            return new Promise((resolve) => {
                if (monacoInstance) return resolve(monacoInstance);
                require.config({ paths: { 'vs': './vendor/monaco/min/vs' } });
                require(['vs/editor/editor.main'], function() {
                    monacoInstance = monaco;
                    registerScriptProviders(monaco);
                    resolve(monaco);
                });
            });
        };

