// Pioneer Web Messenger - Full Featured Version
const API_BASE = window.location.origin;
const WS_BASE = API_BASE.replace('http', 'ws');

// State
let state = {
    token: localStorage.getItem('pioneer_token'),
    userId: localStorage.getItem('pioneer_userId'),
    user: null,
    chats: [],
    activeChat: null,
    messages: [],
    users: {},
    ws: null,
    sessionId: null,
    sessionCode: null,
    currentView: 'chats',
    tasks: [],
    finance: [],
    typingUsers: {},
    unreadCounts: {}
};

// Initialize app
async function init() {
    if (state.token && state.userId) {
        await loadUserData();
    } else {
        showLoginScreen();
    }
}

// ==================== LOGIN SCREEN ====================
function showLoginScreen() {
    const app = document.getElementById('app');
    app.innerHTML = `
        <div class="min-h-screen flex items-center justify-center p-4">
            <div class="glass rounded-3xl p-8 md:p-12 max-w-md w-full text-center relative overflow-hidden">
                <div class="absolute -top-20 -right-20 w-40 h-40 bg-pioneer-500/20 rounded-full blur-3xl"></div>
                <div class="absolute -bottom-20 -left-20 w-40 h-40 bg-purple-500/20 rounded-full blur-3xl"></div>
                
                <div class="relative mb-8">
                    <div class="w-24 h-24 mx-auto rounded-2xl bg-gradient-to-br from-pioneer-500 to-purple-600 flex items-center justify-center glow">
                        <i class="fas fa-rocket text-4xl text-white float"></i>
                    </div>
                </div>
                
                <h1 class="text-4xl font-bold mb-2 bg-gradient-to-r from-pioneer-400 to-purple-400 bg-clip-text text-transparent">Pioneer</h1>
                <p class="text-dark-400 mb-8">Безопасный мессенджер нового поколения</p>
                
                <div id="login-form">
                    <div class="mb-6">
                        <div id="session-code" class="text-4xl font-mono font-bold tracking-[0.5em] text-pioneer-400 mb-2">------</div>
                        <p class="text-dark-500 text-sm">Введите этот код в приложении</p>
                    </div>
                    <div id="login-status" class="mb-6">
                        <div class="flex items-center justify-center gap-2 text-dark-400">
                            <div class="w-2 h-2 bg-green-500 rounded-full pulse"></div>
                            <span>Ожидание авторизации...</span>
                        </div>
                    </div>
                </div>
                
                <div class="mt-8 pt-6 border-t border-dark-700/50">
                    <p class="text-dark-500 text-sm mb-4">Как войти:</p>
                    <div class="space-y-3 text-left">
                        <div class="flex items-center gap-3 text-dark-400 text-sm">
                            <div class="w-8 h-8 rounded-lg bg-pioneer-500/20 flex items-center justify-center text-pioneer-400 font-bold">1</div>
                            <span>Откройте Pioneer на телефоне</span>
                        </div>
                        <div class="flex items-center gap-3 text-dark-400 text-sm">
                            <div class="w-8 h-8 rounded-lg bg-pioneer-500/20 flex items-center justify-center text-pioneer-400 font-bold">2</div>
                            <span>Настройки → Связанные устройства</span>
                        </div>
                        <div class="flex items-center gap-3 text-dark-400 text-sm">
                            <div class="w-8 h-8 rounded-lg bg-pioneer-500/20 flex items-center justify-center text-pioneer-400 font-bold">3</div>
                            <span>Введите код выше</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;
    createSession();
}

async function createSession() {
    try {
        const res = await fetch(`${API_BASE}/api/web/session/create`, { 
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        state.sessionId = data.sessionId;
        state.sessionCode = data.sessionCode;
        document.getElementById('session-code').textContent = data.sessionCode;
        pollSessionStatus();
    } catch (err) {
        console.error('Session error:', err);
        document.getElementById('login-status').innerHTML = `
            <div class="text-red-400 mb-4"><i class="fas fa-exclamation-circle mr-2"></i>Ошибка подключения</div>
            <button onclick="createSession()" class="btn-primary px-6 py-2 rounded-xl text-sm"><i class="fas fa-redo mr-2"></i>Повторить</button>
        `;
    }
}

async function pollSessionStatus() {
    if (!state.sessionId) return;
    try {
        const res = await fetch(`${API_BASE}/api/web/session/${state.sessionId}/status`);
        const data = await res.json();
        if (data.status === 'authorized') {
            state.token = data.token;
            state.userId = data.userId;
            localStorage.setItem('pioneer_token', data.token);
            localStorage.setItem('pioneer_userId', data.userId);
            await loadUserData();
        } else if (data.status === 'expired') {
            document.getElementById('login-status').innerHTML = `
                <div class="text-yellow-400 mb-4"><i class="fas fa-clock mr-2"></i>Сессия истекла</div>
                <button onclick="createSession()" class="btn-primary px-6 py-2 rounded-xl text-sm">Получить новый код</button>
            `;
        } else {
            setTimeout(pollSessionStatus, 2000);
        }
    } catch (err) {
        setTimeout(pollSessionStatus, 3000);
    }
}

// ==================== LOAD DATA ====================
async function loadUserData() {
    try {
        const [userRes, chatsRes, usersRes] = await Promise.all([
            fetch(`${API_BASE}/api/users/${state.userId}`, { headers: { 'Authorization': `Bearer ${state.token}` } }),
            fetch(`${API_BASE}/api/chats`, { headers: { 'Authorization': `Bearer ${state.token}` } }),
            fetch(`${API_BASE}/api/users`, { headers: { 'Authorization': `Bearer ${state.token}` } })
        ]);
        if (!userRes.ok) { logout(); return; }
        state.user = await userRes.json();
        state.chats = await chatsRes.json();
        const users = await usersRes.json();
        state.users = {};
        users.forEach(u => state.users[u.id] = u);
        await Promise.all([loadTasks(), loadFinance()]);
        connectWebSocket();
        renderApp();
    } catch (err) {
        console.error('Load error:', err);
        logout();
    }
}

async function loadTasks() {
    try {
        const res = await fetch(`${API_BASE}/api/tasks`, { headers: { 'Authorization': `Bearer ${state.token}` } });
        if (res.ok) state.tasks = await res.json();
    } catch (e) {}
}

async function loadFinance() {
    try {
        const res = await fetch(`${API_BASE}/api/finance`, { headers: { 'Authorization': `Bearer ${state.token}` } });
        if (res.ok) state.finance = await res.json();
    } catch (e) {}
}

// ==================== WEBSOCKET ====================
function connectWebSocket() {
    if (state.ws) state.ws.close();
    state.ws = new WebSocket(`${WS_BASE}/ws/${state.userId}`);
    state.ws.onopen = () => { console.log('WS connected'); updateConnectionStatus(true); };
    state.ws.onmessage = (event) => {
        try { handleWsMessage(JSON.parse(event.data)); } catch (e) {}
    };
    state.ws.onclose = () => { updateConnectionStatus(false); setTimeout(connectWebSocket, 3000); };
}

function handleWsMessage(msg) {
    switch (msg.type) {
        case 'new_message':
            if (state.activeChat) loadMessages(state.activeChat.id);
            loadChats();
            break;
        case 'typing':
            handleTypingIndicator(msg.payload);
            break;
        case 'user_online':
            if (state.users[msg.payload]) { state.users[msg.payload].isOnline = true; renderChatList(); }
            break;
        case 'user_offline':
            if (state.users[msg.payload]) { state.users[msg.payload].isOnline = false; renderChatList(); }
            break;
    }
}

function handleTypingIndicator(payload) {
    try {
        const data = JSON.parse(payload);
        if (!state.typingUsers[data.chatId]) state.typingUsers[data.chatId] = [];
        if (!state.typingUsers[data.chatId].includes(data.userId)) {
            state.typingUsers[data.chatId].push(data.userId);
        }
        if (state.activeChat?.id === data.chatId) renderTypingIndicator();
        setTimeout(() => {
            state.typingUsers[data.chatId] = state.typingUsers[data.chatId]?.filter(id => id !== data.userId) || [];
            if (state.activeChat?.id === data.chatId) renderTypingIndicator();
        }, 3000);
    } catch (e) {}
}

function sendTyping() {
    if (!state.ws || !state.activeChat) return;
    state.ws.send(JSON.stringify({ type: 'typing', payload: JSON.stringify({ chatId: state.activeChat.id, userId: state.userId }) }));
}

function updateConnectionStatus(connected) {
    const el = document.getElementById('connection-status');
    if (el) el.className = `w-3 h-3 rounded-full border-2 border-dark-900 ${connected ? 'bg-green-500' : 'bg-red-500'}`;
}

// ==================== AVATAR HELPER ====================
function getAvatarUrl(avatarPath) {
    if (!avatarPath) return null;
    if (avatarPath.startsWith('http')) return avatarPath;
    return `${API_BASE}${avatarPath}`;
}

function renderAvatar(user, size = 12) {
    const url = getAvatarUrl(user?.avatarUrl);
    const initial = (user?.displayName || user?.username || '?')[0].toUpperCase();
    if (url) {
        return `<img src="${url}" class="w-${size} h-${size} rounded-full object-cover" onerror="this.style.display='none';this.nextElementSibling.style.display='flex';">
                <div class="w-${size} h-${size} bg-gradient-to-br from-pioneer-500 to-purple-600 rounded-full items-center justify-center text-white font-bold" style="display:none">${initial}</div>`;
    }
    return `<div class="w-${size} h-${size} bg-gradient-to-br from-pioneer-500 to-purple-600 rounded-full flex items-center justify-center text-white font-bold">${initial}</div>`;
}


// ==================== MAIN APP RENDER ====================
function renderApp() {
    const app = document.getElementById('app');
    const avatarUrl = getAvatarUrl(state.user.avatarUrl);
    
    app.innerHTML = `
        <div class="h-screen flex">
            <div class="w-72 glass border-r border-dark-700/50 flex flex-col">
                <div class="p-4 border-b border-dark-700/50">
                    <div class="flex items-center gap-3">
                        <div class="relative">
                            ${avatarUrl 
                                ? `<img src="${avatarUrl}" class="w-12 h-12 rounded-full object-cover" onerror="this.outerHTML='<div class=\\'w-12 h-12 rounded-full bg-gradient-to-br from-pioneer-500 to-purple-600 flex items-center justify-center text-white font-bold text-lg\\'>${state.user.displayName[0]}</div>'">`
                                : `<div class="w-12 h-12 rounded-full bg-gradient-to-br from-pioneer-500 to-purple-600 flex items-center justify-center text-white font-bold text-lg">${state.user.displayName[0]}</div>`
                            }
                            <div id="connection-status" class="absolute -bottom-0.5 -right-0.5 w-3 h-3 bg-green-500 rounded-full border-2 border-dark-900"></div>
                        </div>
                        <div class="flex-1 min-w-0">
                            <div class="font-semibold truncate flex items-center gap-2">
                                ${state.user.displayName}
                                ${state.user.isVerified ? '<i class="fas fa-check-circle text-blue-400 text-sm"></i>' : ''}
                            </div>
                            <div class="text-xs text-dark-400 truncate">@${state.user.username}</div>
                        </div>
                        <button onclick="logout()" class="p-2 hover:bg-dark-700/50 rounded-lg" title="Выйти">
                            <i class="fas fa-sign-out-alt text-dark-400"></i>
                        </button>
                    </div>
                </div>
                
                <nav class="p-2 border-b border-dark-700/50">
                    <button onclick="switchView('chats')" class="nav-item w-full flex items-center gap-3 px-3 py-2 rounded-lg ${state.currentView === 'chats' ? 'active' : ''}">
                        <i class="fas fa-comments text-pioneer-400 w-5"></i><span>Чаты</span>
                    </button>
                    <button onclick="switchView('tasks')" class="nav-item w-full flex items-center gap-3 px-3 py-2 rounded-lg ${state.currentView === 'tasks' ? 'active' : ''}">
                        <i class="fas fa-tasks text-purple-400 w-5"></i><span>Задачи</span>
                    </button>
                    <button onclick="switchView('finance')" class="nav-item w-full flex items-center gap-3 px-3 py-2 rounded-lg ${state.currentView === 'finance' ? 'active' : ''}">
                        <i class="fas fa-wallet text-green-400 w-5"></i><span>Финансы</span>
                    </button>
                    <button onclick="switchView('map')" class="nav-item w-full flex items-center gap-3 px-3 py-2 rounded-lg ${state.currentView === 'map' ? 'active' : ''}">
                        <i class="fas fa-map-marker-alt text-red-400 w-5"></i><span>Карта</span>
                    </button>
                    <button onclick="switchView('settings')" class="nav-item w-full flex items-center gap-3 px-3 py-2 rounded-lg ${state.currentView === 'settings' ? 'active' : ''}">
                        <i class="fas fa-cog text-dark-400 w-5"></i><span>Настройки</span>
                    </button>
                </nav>
                
                <div class="p-3">
                    <div class="relative">
                        <input type="text" placeholder="Поиск..." class="input-field w-full rounded-xl px-4 py-2 pl-10 text-sm focus:outline-none">
                        <i class="fas fa-search text-dark-500 absolute left-3 top-1/2 -translate-y-1/2"></i>
                    </div>
                </div>
                
                <div id="sidebar-content" class="flex-1 overflow-y-auto scrollbar-hide">${renderSidebarContent()}</div>
            </div>
            
            <div class="flex-1 flex flex-col bg-dark-950/50">
                <div id="main-content" class="flex-1 flex">${renderMainContent()}</div>
            </div>
        </div>
    `;
}

function switchView(view) { state.currentView = view; state.activeChat = null; renderApp(); }
function renderSidebarContent() {
    switch (state.currentView) {
        case 'chats': return renderChatListItems();
        case 'tasks': return renderTaskList();
        case 'finance': return renderFinanceList();
        default: return '';
    }
}
function renderMainContent() {
    switch (state.currentView) {
        case 'chats': return state.activeChat ? renderChatArea() : renderEmptyState('chats');
        case 'tasks': return renderTasksView();
        case 'finance': return renderFinanceView();
        case 'map': return renderMapView();
        case 'settings': return renderSettingsView();
        default: return renderEmptyState('chats');
    }
}
function renderEmptyState(type) {
    return `<div class="flex-1 flex items-center justify-center"><div class="text-center">
        <div class="w-24 h-24 mx-auto mb-6 rounded-2xl bg-dark-800/50 flex items-center justify-center">
            <i class="fas fa-comments text-4xl text-dark-600"></i>
        </div>
        <p class="text-dark-500">Выберите чат для начала общения</p>
    </div></div>`;
}

// ==================== CHAT LIST ====================
function renderChatListItems() {
    if (state.chats.length === 0) return `<div class="p-4 text-center text-dark-500 text-sm">Нет чатов</div>`;
    
    return state.chats.map(chat => {
        const isActive = state.activeChat?.id === chat.id;
        const otherUserId = chat.participants.find(id => id !== state.userId);
        const otherUser = state.users[otherUserId];
        const avatarUrl = getAvatarUrl(otherUser?.avatarUrl);
        const isOnline = otherUser?.isOnline;
        const isVerified = otherUser?.isVerified;
        
        return `
            <div class="chat-item p-3 mx-2 my-1 rounded-xl cursor-pointer ${isActive ? 'active' : ''}" onclick="selectChat('${chat.id}')">
                <div class="flex items-center gap-3">
                    <div class="relative">
                        ${avatarUrl 
                            ? `<img src="${avatarUrl}" class="w-12 h-12 rounded-full object-cover" onerror="this.outerHTML='<div class=\\'w-12 h-12 bg-gradient-to-br from-pioneer-500 to-purple-600 rounded-full flex items-center justify-center text-white font-bold\\'>${chat.name[0]}</div>'">`
                            : `<div class="w-12 h-12 bg-gradient-to-br from-pioneer-500 to-purple-600 rounded-full flex items-center justify-center text-white font-bold">${chat.name[0]}</div>`
                        }
                        ${isOnline ? '<div class="online-indicator absolute -bottom-0.5 -right-0.5 w-3.5 h-3.5 rounded-full border-2 border-dark-900"></div>' : ''}
                    </div>
                    <div class="flex-1 min-w-0">
                        <div class="flex items-center gap-1.5">
                            <span class="font-medium truncate">${chat.name}</span>
                            ${isVerified ? '<i class="fas fa-check-circle text-blue-400 text-xs"></i>' : ''}
                        </div>
                        <div class="text-sm text-dark-400 truncate">Нажмите чтобы открыть</div>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

function renderChatList() {
    const list = document.getElementById('sidebar-content');
    if (list && state.currentView === 'chats') list.innerHTML = renderChatListItems();
}

async function loadChats() {
    try {
        const res = await fetch(`${API_BASE}/api/chats`, { headers: { 'Authorization': `Bearer ${state.token}` } });
        state.chats = await res.json();
        renderChatList();
    } catch (e) {}
}

// ==================== CHAT AREA ====================
async function selectChat(chatId) {
    const chat = state.chats.find(c => c.id === chatId);
    if (!chat) return;
    state.activeChat = chat;
    renderChatList();
    await loadMessages(chatId);
    document.getElementById('main-content').innerHTML = renderChatArea();
    scrollToBottom();
}

async function loadMessages(chatId) {
    try {
        const res = await fetch(`${API_BASE}/api/messages/${chatId}`, { headers: { 'Authorization': `Bearer ${state.token}` } });
        state.messages = (await res.json()).reverse();
        if (state.activeChat?.id === chatId) {
            const container = document.getElementById('messages-container');
            if (container) { container.innerHTML = renderMessages(); scrollToBottom(); }
        }
    } catch (e) {}
}

function renderChatArea() {
    const chat = state.activeChat;
    if (!chat) return renderEmptyState('chats');
    const otherUserId = chat.participants.find(id => id !== state.userId);
    const otherUser = state.users[otherUserId];
    const avatarUrl = getAvatarUrl(otherUser?.avatarUrl);
    const isOnline = otherUser?.isOnline;
    const isVerified = otherUser?.isVerified;
    
    return `
        <div class="flex flex-col h-full w-full">
            <div class="glass-light p-4 border-b border-dark-700/50">
                <div class="flex items-center gap-4">
                    <div class="relative">
                        ${avatarUrl 
                            ? `<img src="${avatarUrl}" class="w-12 h-12 rounded-full object-cover" onerror="this.outerHTML='<div class=\\'w-12 h-12 bg-gradient-to-br from-pioneer-500 to-purple-600 rounded-full flex items-center justify-center text-white font-bold text-lg\\'>${chat.name[0]}</div>'">`
                            : `<div class="w-12 h-12 bg-gradient-to-br from-pioneer-500 to-purple-600 rounded-full flex items-center justify-center text-white font-bold text-lg">${chat.name[0]}</div>`
                        }
                        ${isOnline ? '<div class="online-indicator absolute -bottom-0.5 -right-0.5 w-3.5 h-3.5 rounded-full border-2 border-dark-800"></div>' : ''}
                    </div>
                    <div class="flex-1">
                        <div class="flex items-center gap-2">
                            <span class="font-semibold text-lg">${chat.name}</span>
                            ${isVerified ? '<i class="fas fa-check-circle text-blue-400"></i>' : ''}
                        </div>
                        <div class="text-sm text-dark-400">${isOnline ? '<span class="text-green-400">онлайн</span>' : 'был(а) недавно'}</div>
                    </div>
                    <div class="flex items-center gap-2">
                        <button class="p-2 hover:bg-dark-700/50 rounded-lg"><i class="fas fa-phone text-dark-400"></i></button>
                        <button class="p-2 hover:bg-dark-700/50 rounded-lg"><i class="fas fa-video text-dark-400"></i></button>
                    </div>
                </div>
            </div>
            
            <div id="messages-container" class="flex-1 overflow-y-auto p-4 space-y-3 scrollbar-hide">${renderMessages()}</div>
            
            <div id="typing-indicator" class="px-4 h-6">${renderTypingIndicatorContent()}</div>
            
            <div class="glass-light p-4 border-t border-dark-700/50">
                <form onsubmit="sendMessage(event)" class="flex items-center gap-3">
                    <button type="button" class="p-2 hover:bg-dark-700/50 rounded-lg"><i class="fas fa-paperclip text-dark-400"></i></button>
                    <input type="text" id="message-input" placeholder="Написать сообщение..." 
                        class="input-field flex-1 rounded-xl px-4 py-3 focus:outline-none" autocomplete="off" oninput="sendTyping()">
                    <button type="submit" class="btn-primary p-3 rounded-xl"><i class="fas fa-paper-plane"></i></button>
                </form>
            </div>
        </div>
    `;
}

function renderTypingIndicator() {
    const el = document.getElementById('typing-indicator');
    if (el) el.innerHTML = renderTypingIndicatorContent();
}

function renderTypingIndicatorContent() {
    if (!state.activeChat) return '';
    const typing = state.typingUsers[state.activeChat.id];
    if (!typing || typing.length === 0) return '';
    const names = typing.map(id => state.users[id]?.displayName || 'Кто-то').slice(0, 2);
    return `<div class="flex items-center gap-2 text-sm text-pioneer-400">
        <div class="flex gap-1">
            <span class="typing-dot w-1.5 h-1.5 bg-pioneer-400 rounded-full"></span>
            <span class="typing-dot w-1.5 h-1.5 bg-pioneer-400 rounded-full"></span>
            <span class="typing-dot w-1.5 h-1.5 bg-pioneer-400 rounded-full"></span>
        </div>
        <span>${names.join(', ')} печатает...</span>
    </div>`;
}


// ==================== MESSAGES ====================
function renderMessages() {
    if (state.messages.length === 0) {
        return `<div class="text-center py-12">
            <div class="w-16 h-16 mx-auto mb-4 rounded-2xl bg-dark-800/50 flex items-center justify-center">
                <i class="fas fa-comments text-2xl text-dark-600"></i>
            </div>
            <p class="text-dark-500">Нет сообщений</p>
        </div>`;
    }
    
    return state.messages.map(msg => {
        const isOwn = msg.senderId === state.userId;
        const sender = state.users[msg.senderId];
        const time = new Date(msg.timestamp).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
        const avatarUrl = getAvatarUrl(sender?.avatarUrl);
        
        // Декодируем сообщение - пробуем разные варианты
        let content = decodeMessage(msg.encryptedContent, msg.type);
        
        return `
            <div class="flex ${isOwn ? 'justify-end' : 'justify-start'}">
                ${!isOwn ? `
                    <div class="mr-2 flex-shrink-0">
                        ${avatarUrl 
                            ? `<img src="${avatarUrl}" class="w-8 h-8 rounded-full object-cover" onerror="this.outerHTML='<div class=\\'w-8 h-8 bg-gradient-to-br from-pioneer-500 to-purple-600 rounded-full flex items-center justify-center text-white font-bold text-xs\\'>${(sender?.displayName || '?')[0]}</div>'">`
                            : `<div class="w-8 h-8 bg-gradient-to-br from-pioneer-500 to-purple-600 rounded-full flex items-center justify-center text-white font-bold text-xs">${(sender?.displayName || '?')[0]}</div>`
                        }
                    </div>
                ` : ''}
                <div class="${isOwn ? 'message-out' : 'message-in'} px-4 py-2 max-w-[70%] shadow-lg">
                    ${!isOwn ? `
                        <div class="flex items-center gap-1.5 mb-1">
                            <span class="text-xs font-medium text-pioneer-400">${sender?.displayName || 'Пользователь'}</span>
                            ${sender?.isVerified ? '<i class="fas fa-check-circle text-blue-400 text-xs"></i>' : ''}
                        </div>
                    ` : ''}
                    <div class="break-words">${escapeHtml(content)}</div>
                    <div class="flex items-center justify-end gap-1 mt-1">
                        <span class="text-xs ${isOwn ? 'text-white/60' : 'text-dark-500'}">${time}</span>
                        ${isOwn ? `<i class="fas fa-check-double text-xs ${msg.status === 'READ' ? 'text-blue-400' : 'text-white/40'}"></i>` : ''}
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

function decodeMessage(content, type) {
    if (!content) return getMessagePreview(type);
    
    // Сначала пробуем как обычный текст (Android отправляет текст напрямую)
    // Проверяем, похоже ли это на base64
    const isBase64 = /^[A-Za-z0-9+/=]+$/.test(content) && content.length > 20;
    
    if (isBase64) {
        try {
            // Пробуем декодировать base64
            const decoded = atob(content);
            // Проверяем, является ли результат читаемым текстом
            if (/^[\x20-\x7E\u0400-\u04FF\s]+$/.test(decoded)) {
                return decoded;
            }
            // Пробуем UTF-8 декодирование
            try {
                return decodeURIComponent(escape(decoded));
            } catch {
                return decoded;
            }
        } catch {
            // Не base64, возвращаем как есть
            return content;
        }
    }
    
    // Возвращаем как обычный текст
    return content;
}

function getMessagePreview(type) {
    switch (type) {
        case 'IMAGE': return '🖼 Изображение';
        case 'VIDEO': return '🎬 Видео';
        case 'VOICE': return '🎤 Голосовое';
        case 'VIDEO_NOTE': return '📹 Видеосообщение';
        case 'FILE': return '📎 Файл';
        default: return '💬 Сообщение';
    }
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function scrollToBottom() {
    const container = document.getElementById('messages-container');
    if (container) setTimeout(() => container.scrollTop = container.scrollHeight, 100);
}

// ==================== SEND MESSAGE ====================
async function sendMessage(event) {
    event.preventDefault();
    const input = document.getElementById('message-input');
    const text = input.value.trim();
    if (!text || !state.activeChat) return;
    input.value = '';
    
    // Отправляем текст напрямую (как Android)
    const tempMsg = {
        id: 'temp-' + Date.now(),
        chatId: state.activeChat.id,
        senderId: state.userId,
        encryptedContent: text,
        timestamp: Date.now(),
        type: 'TEXT',
        status: 'SENDING'
    };
    state.messages.push(tempMsg);
    
    const container = document.getElementById('messages-container');
    if (container) { container.innerHTML = renderMessages(); scrollToBottom(); }
    
    try {
        await fetch(`${API_BASE}/api/messages`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${state.token}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({ chatId: state.activeChat.id, encryptedContent: text, nonce: '', type: 'TEXT' })
        });
        await loadMessages(state.activeChat.id);
    } catch (err) { console.error('Send error:', err); }
}

// ==================== TASKS ====================
function renderTaskList() {
    if (state.tasks.length === 0) return `<div class="p-4 text-center text-dark-500 text-sm">Нет задач</div>`;
    return state.tasks.map(task => `
        <div class="glass-light p-3 mx-2 my-1 rounded-xl cursor-pointer">
            <div class="flex items-start gap-3">
                <i class="fas ${task.status === 'DONE' ? 'fa-check-circle text-green-400' : 'fa-circle text-dark-500'} mt-1"></i>
                <div class="flex-1 min-w-0">
                    <div class="font-medium truncate ${task.status === 'DONE' ? 'line-through text-dark-500' : ''}">${task.title}</div>
                    <div class="text-xs text-dark-500 mt-1">${task.dueDate ? new Date(task.dueDate).toLocaleDateString('ru-RU') : 'Без срока'}</div>
                </div>
            </div>
        </div>
    `).join('');
}

function renderTasksView() {
    return `<div class="flex-1 p-6">
        <div class="flex items-center justify-between mb-6">
            <h2 class="text-2xl font-bold">Задачи</h2>
            <button onclick="showCreateTaskModal()" class="btn-primary px-4 py-2 rounded-xl"><i class="fas fa-plus mr-2"></i>Новая</button>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div class="glass rounded-2xl p-4">
                <h3 class="font-semibold mb-4"><span class="w-3 h-3 bg-dark-500 rounded-full inline-block mr-2"></span>К выполнению</h3>
                <div class="space-y-2">${state.tasks.filter(t => t.status === 'PENDING').map(t => `<div class="glass-light p-3 rounded-xl"><div class="font-medium text-sm">${t.title}</div></div>`).join('')}</div>
            </div>
            <div class="glass rounded-2xl p-4">
                <h3 class="font-semibold mb-4"><span class="w-3 h-3 bg-yellow-500 rounded-full inline-block mr-2"></span>В работе</h3>
                <div class="space-y-2">${state.tasks.filter(t => t.status === 'IN_PROGRESS').map(t => `<div class="glass-light p-3 rounded-xl"><div class="font-medium text-sm">${t.title}</div></div>`).join('')}</div>
            </div>
            <div class="glass rounded-2xl p-4">
                <h3 class="font-semibold mb-4"><span class="w-3 h-3 bg-green-500 rounded-full inline-block mr-2"></span>Выполнено</h3>
                <div class="space-y-2">${state.tasks.filter(t => t.status === 'DONE').map(t => `<div class="glass-light p-3 rounded-xl"><div class="font-medium text-sm line-through text-dark-500">${t.title}</div></div>`).join('')}</div>
            </div>
        </div>
    </div>`;
}

function showCreateTaskModal() {
    const modal = document.createElement('div');
    modal.id = 'task-modal';
    modal.className = 'fixed inset-0 bg-black/50 flex items-center justify-center z-50';
    modal.innerHTML = `
        <div class="glass rounded-2xl p-6 max-w-md w-full mx-4">
            <h3 class="text-xl font-bold mb-4">Новая задача</h3>
            <form onsubmit="createTask(event)">
                <input type="text" id="task-title" placeholder="Название" class="input-field w-full rounded-xl px-4 py-3 mb-3" required>
                <textarea id="task-desc" placeholder="Описание" class="input-field w-full rounded-xl px-4 py-3 mb-3 h-24 resize-none"></textarea>
                <select id="task-priority" class="input-field w-full rounded-xl px-4 py-3 mb-3">
                    <option value="LOW">Низкий</option><option value="MEDIUM" selected>Средний</option><option value="HIGH">Высокий</option>
                </select>
                <div class="flex gap-3">
                    <button type="button" onclick="closeModal('task-modal')" class="flex-1 py-3 rounded-xl bg-dark-700">Отмена</button>
                    <button type="submit" class="flex-1 btn-primary py-3 rounded-xl">Создать</button>
                </div>
            </form>
        </div>
    `;
    document.body.appendChild(modal);
}

async function createTask(event) {
    event.preventDefault();
    try {
        await fetch(`${API_BASE}/api/tasks`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${state.token}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({ title: document.getElementById('task-title').value, description: document.getElementById('task-desc').value, priority: document.getElementById('task-priority').value })
        });
        closeModal('task-modal');
        await loadTasks();
        renderApp();
    } catch (e) {}
}

function closeModal(id) { document.getElementById(id)?.remove(); }

// ==================== FINANCE ====================
function renderFinanceList() {
    if (state.finance.length === 0) return `<div class="p-4 text-center text-dark-500 text-sm">Нет записей</div>`;
    return state.finance.map(r => `
        <div class="glass-light p-3 mx-2 my-1 rounded-xl">
            <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-xl ${r.type === 'INCOME' ? 'bg-green-500/20' : 'bg-red-500/20'} flex items-center justify-center">
                    <i class="fas ${r.type === 'INCOME' ? 'fa-arrow-down text-green-400' : 'fa-arrow-up text-red-400'}"></i>
                </div>
                <div class="flex-1"><div class="font-medium truncate">${r.description || r.category}</div></div>
                <div class="font-bold ${r.type === 'INCOME' ? 'text-green-400' : 'text-red-400'}">${r.type === 'INCOME' ? '+' : '-'}${r.amount}₽</div>
            </div>
        </div>
    `).join('');
}

function renderFinanceView() {
    const income = state.finance.filter(r => r.type === 'INCOME').reduce((s, r) => s + r.amount, 0);
    const expense = state.finance.filter(r => r.type === 'EXPENSE').reduce((s, r) => s + r.amount, 0);
    return `<div class="flex-1 p-6">
        <h2 class="text-2xl font-bold mb-6">Финансы</h2>
        <div class="grid grid-cols-3 gap-4 mb-6">
            <div class="glass rounded-2xl p-6"><div class="text-dark-400 text-sm mb-2">Баланс</div><div class="text-3xl font-bold ${income - expense >= 0 ? 'text-green-400' : 'text-red-400'}">${income - expense}₽</div></div>
            <div class="glass rounded-2xl p-6"><div class="text-dark-400 text-sm mb-2">Доходы</div><div class="text-3xl font-bold text-green-400">+${income}₽</div></div>
            <div class="glass rounded-2xl p-6"><div class="text-dark-400 text-sm mb-2">Расходы</div><div class="text-3xl font-bold text-red-400">-${expense}₽</div></div>
        </div>
    </div>`;
}

function renderMapView() {
    return `<div class="flex-1 p-6">
        <h2 class="text-2xl font-bold mb-6">Карта</h2>
        <div class="glass rounded-2xl overflow-hidden" style="height: calc(100vh - 200px);">
            <iframe src="https://www.openstreetmap.org/export/embed.html?bbox=37.3,55.6,37.9,55.9&layer=mapnik" class="w-full h-full border-0"></iframe>
        </div>
    </div>`;
}

function renderSettingsView() {
    return `<div class="flex-1 p-6 max-w-2xl">
        <h2 class="text-2xl font-bold mb-6">Настройки</h2>
        <div class="glass rounded-2xl p-6 mb-4">
            <h3 class="font-semibold mb-4"><i class="fas fa-user text-pioneer-400 mr-2"></i>Профиль</h3>
            <div class="flex items-center gap-4">
                ${getAvatarUrl(state.user.avatarUrl) 
                    ? `<img src="${getAvatarUrl(state.user.avatarUrl)}" class="w-20 h-20 rounded-full object-cover">`
                    : `<div class="w-20 h-20 rounded-full bg-gradient-to-br from-pioneer-500 to-purple-600 flex items-center justify-center text-white font-bold text-2xl">${state.user.displayName[0]}</div>`
                }
                <div>
                    <div class="font-semibold text-lg">${state.user.displayName} ${state.user.isVerified ? '<i class="fas fa-check-circle text-blue-400"></i>' : ''}</div>
                    <div class="text-dark-400">@${state.user.username}</div>
                </div>
            </div>
        </div>
        <button onclick="logout()" class="w-full py-4 rounded-xl bg-red-500/20 text-red-400 hover:bg-red-500/30"><i class="fas fa-sign-out-alt mr-2"></i>Выйти</button>
    </div>`;
}

function logout() {
    localStorage.removeItem('pioneer_token');
    localStorage.removeItem('pioneer_userId');
    state.token = state.userId = state.user = null;
    if (state.ws) state.ws.close();
    showLoginScreen();
}

// Start
init();
