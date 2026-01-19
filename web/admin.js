// MKR Admin Panel JavaScript

const API_URL = window.location.origin; // https://kluboksrm.ru
let authToken = null;
let currentUserId = null;

console.log('Admin Panel loaded. API URL:', API_URL);

// Check if already logged in
window.onload = function() {
    authToken = localStorage.getItem('adminToken');
    currentUserId = localStorage.getItem('adminUserId');
    
    if (authToken) {
        showDashboard();
        loadDashboardData();
    }
};

// Login
async function login() {
    const username = document.getElementById('loginUsername').value;
    const password = document.getElementById('loginPassword').value;
    const errorEl = document.getElementById('loginError');
    
    errorEl.textContent = '';
    
    if (!username || !password) {
        errorEl.textContent = 'Заполните все поля';
        return;
    }
    
    try {
        // Try email login first
        const response = await fetch(`${API_URL}/api/auth/login/email`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                email: username,
                password: password
            })
        });
        
        const data = await response.json();
        
        if (response.ok && data.token) {
            // Check if user is admin
            if (data.accessLevel >= 10) {
                authToken = data.token;
                currentUserId = data.userId;
                localStorage.setItem('adminToken', authToken);
                localStorage.setItem('adminUserId', currentUserId);
                showDashboard();
                loadDashboardData();
            } else {
                errorEl.textContent = 'Недостаточно прав доступа';
            }
        } else {
            errorEl.textContent = data.error || 'Ошибка входа';
        }
    } catch (error) {
        console.error('Login error:', error);
        errorEl.textContent = 'Ошибка подключения к серверу';
    }
}

// Logout
function logout() {
    authToken = null;
    currentUserId = null;
    localStorage.removeItem('adminToken');
    localStorage.removeItem('adminUserId');
    document.getElementById('loginContainer').style.display = 'block';
    document.getElementById('adminContainer').style.display = 'none';
}

// Show Dashboard
function showDashboard() {
    document.getElementById('loginContainer').style.display = 'none';
    document.getElementById('adminContainer').style.display = 'block';
}

// Load Dashboard Data
async function loadDashboardData() {
    await Promise.all([
        loadStats(),
        loadUsers(),
        loadChats()
    ]);
    
    // Refresh every 30 seconds
    setInterval(() => {
        loadStats();
        loadUsers();
        loadChats();
    }, 30000);
}

// Load Stats
async function loadStats() {
    try {
        const response = await fetch(`${API_URL}/api/admin/stats`, {
            headers: {
                'Authorization': `Bearer ${authToken}`
            }
        });
        
        if (response.ok) {
            const stats = await response.json();
            document.getElementById('totalUsers').textContent = stats.totalUsers || 0;
            document.getElementById('onlineUsers').textContent = stats.onlineUsers || 0;
            document.getElementById('totalChats').textContent = stats.totalChats || 0;
            document.getElementById('totalMessages').textContent = stats.todayMessages || 0;
            document.getElementById('totalChannels').textContent = stats.totalChannels || 0;
            document.getElementById('totalCalls').textContent = stats.todayCalls || 0;
            document.getElementById('memoryUsage').textContent = formatBytes(stats.usedMemory || 0);
            document.getElementById('cpuUsage').textContent = (stats.cpuUsage || 0) + '%';
            
            if (stats.uptime) {
                document.getElementById('uptime').textContent = formatUptime(stats.uptime);
            }
        } else {
            // Fallback: load basic stats
            await loadBasicStats();
        }
    } catch (error) {
        console.error('Error loading stats:', error);
        await loadBasicStats();
    }
}

// Load basic stats if admin endpoint doesn't exist
async function loadBasicStats() {
    try {
        // Get users count
        const usersResponse = await fetch(`${API_URL}/api/users`, {
            headers: {
                'Authorization': `Bearer ${authToken}`
            }
        });
        
        if (usersResponse.ok) {
            const users = await usersResponse.json();
            document.getElementById('totalUsers').textContent = users.length;
            
            // Count online users
            const onlineCount = users.filter(u => u.isOnline).length;
            document.getElementById('onlineUsers').textContent = onlineCount;
        }
        
        // Get chats count
        const chatsResponse = await fetch(`${API_URL}/api/chats`, {
            headers: {
                'Authorization': `Bearer ${authToken}`
            }
        });
        
        if (chatsResponse.ok) {
            const chats = await chatsResponse.json();
            document.getElementById('totalChats').textContent = chats.length;
        }
        
    } catch (error) {
        console.error('Error loading basic stats:', error);
    }
}

// Load Users
async function loadUsers() {
    try {
        const response = await fetch(`${API_URL}/api/users`, {
            headers: {
                'Authorization': `Bearer ${authToken}`
            }
        });
        
        if (response.ok) {
            const users = await response.json();
            renderUsers(users);
        } else if (response.status === 401) {
            logout();
        }
    } catch (error) {
        console.error('Error loading users:', error);
        document.getElementById('userList').innerHTML = '<div class="error">Ошибка загрузки пользователей</div>';
    }
}

// Render Users
function renderUsers(users) {
    const userList = document.getElementById('userList');
    
    if (users.length === 0) {
        userList.innerHTML = '<div class="loading">Нет пользователей</div>';
        return;
    }
    
    // Sort by access level and online status
    users.sort((a, b) => {
        if (a.accessLevel !== b.accessLevel) {
            return b.accessLevel - a.accessLevel;
        }
        return b.isOnline - a.isOnline;
    });
    
    userList.innerHTML = users.map(user => `
        <div class="user-item">
            <div class="user-info">
                <div class="user-name">
                    ${user.displayName || user.username}
                    ${user.isOnline ? '🟢' : '⚫'}
                    ${user.accessLevel >= 10 ? '<span class="badge admin">ADMIN</span>' : ''}
                    ${user.isVerified ? '<span class="badge verified">✓</span>' : ''}
                    ${user.isBanned ? '<span class="badge banned">BANNED</span>' : ''}
                </div>
                <div class="user-email">
                    @${user.username} ${user.email ? `• ${user.email}` : ''}
                </div>
            </div>
            <div>
                ${!user.isVerified ? `<button class="btn btn-verify" onclick="verifyUser('${user.id}')">✓ Verify</button>` : ''}
                ${!user.isBanned ? `<button class="btn btn-ban" onclick="banUser('${user.id}')">🚫 Ban</button>` : ''}
                ${user.isBanned ? `<button class="btn btn-unban" onclick="unbanUser('${user.id}')">✓ Unban</button>` : ''}
            </div>
        </div>
    `).join('');
}

// Load Chats
async function loadChats() {
    try {
        const response = await fetch(`${API_URL}/api/chats`, {
            headers: {
                'Authorization': `Bearer ${authToken}`
            }
        });
        
        if (response.ok) {
            const chats = await response.json();
            renderChats(chats);
        }
    } catch (error) {
        console.error('Error loading chats:', error);
        document.getElementById('chatList').innerHTML = '<div class="error">Ошибка загрузки чатов</div>';
    }
}

// Render Chats
function renderChats(chats) {
    const chatList = document.getElementById('chatList');
    
    if (chats.length === 0) {
        chatList.innerHTML = '<div class="loading">Нет чатов</div>';
        return;
    }
    
    // Show last 10 chats
    const recentChats = chats.slice(0, 10);
    
    chatList.innerHTML = recentChats.map(chat => `
        <div class="chat-item">
            <div class="user-info">
                <div class="user-name">
                    ${chat.type === 'direct' ? '💬' : '👥'} ${chat.name}
                </div>
                <div class="user-email">
                    ${chat.participants.length} участников • ${new Date(chat.createdAt).toLocaleDateString('ru')}
                </div>
            </div>
        </div>
    `).join('');
}

// Verify User
async function verifyUser(userId) {
    if (!confirm('Верифицировать этого пользователя?')) return;
    
    try {
        const response = await fetch(`${API_URL}/api/users/${userId}/verify`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${authToken}`
            }
        });
        
        if (response.ok) {
            alert('Пользователь верифицирован');
            loadUsers();
        } else {
            alert('Ошибка верификации');
        }
    } catch (error) {
        console.error('Error verifying user:', error);
        alert('Ошибка подключения');
    }
}

// Ban User
async function banUser(userId) {
    const reason = prompt('Причина бана:');
    if (!reason) return;
    
    try {
        const response = await fetch(`${API_URL}/api/users/${userId}/ban`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${authToken}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ reason })
        });
        
        if (response.ok) {
            alert('Пользователь забанен');
            loadUsers();
        } else {
            alert('Ошибка бана');
        }
    } catch (error) {
        console.error('Error banning user:', error);
        alert('Ошибка подключения');
    }
}

// Unban User
async function unbanUser(userId) {
    if (!confirm('Разбанить этого пользователя?')) return;
    
    try {
        const response = await fetch(`${API_URL}/api/users/${userId}/ban`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${authToken}`
            }
        });
        
        if (response.ok) {
            alert('Пользователь разбанен');
            loadUsers();
        } else {
            alert('Ошибка разбана');
        }
    } catch (error) {
        console.error('Error unbanning user:', error);
        alert('Ошибка подключения');
    }
}

// Format uptime
function formatUptime(seconds) {
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    
    if (days > 0) {
        return `${days}д ${hours}ч ${minutes}м`;
    } else if (hours > 0) {
        return `${hours}ч ${minutes}м`;
    } else {
        return `${minutes}м`;
    }
}

// Handle Enter key on login
document.addEventListener('DOMContentLoaded', function() {
    const loginPassword = document.getElementById('loginPassword');
    if (loginPassword) {
        loginPassword.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                login();
            }
        });
    }
});


// Tab switching
function switchTab(tab) {
    // Update tab buttons
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    event.target.classList.add('active');
    
    // Show/hide tabs
    document.getElementById('overviewTab').style.display = 'none';
    document.getElementById('systemTab').style.display = 'none';
    document.getElementById('activityTab').style.display = 'none';
    document.getElementById('logsTab').style.display = 'none';
    
    if (tab === 'overview') {
        document.getElementById('overviewTab').style.display = 'grid';
    } else if (tab === 'system') {
        document.getElementById('systemTab').style.display = 'block';
        loadSystemInfo();
    } else if (tab === 'activity') {
        document.getElementById('activityTab').style.display = 'block';
        loadActivityInfo();
    } else if (tab === 'logs') {
        document.getElementById('logsTab').style.display = 'block';
        loadLogs();
    }
}

// Load logs
let logsInterval = null;
async function loadLogs() {
    try {
        const response = await fetch(`${API_URL}/api/admin/logs?lines=100`, {
            headers: {
                'Authorization': `Bearer ${authToken}`
            }
        });
        
        if (response.ok) {
            const data = await response.json();
            renderLogs(data.logs || []);
        } else if (response.status === 401) {
            logout();
        } else {
            document.getElementById('logsContainer').innerHTML = '<div class="error">Ошибка загрузки логов</div>';
        }
    } catch (error) {
        console.error('Error loading logs:', error);
        document.getElementById('logsContainer').innerHTML = '<div class="error">Ошибка подключения</div>';
    }
    
    // Auto-refresh every 5 seconds
    if (!logsInterval) {
        logsInterval = setInterval(loadLogs, 5000);
    }
}

// Render logs
function renderLogs(logs) {
    const container = document.getElementById('logsContainer');
    const autoScroll = document.getElementById('autoScrollLogs').checked;
    
    if (logs.length === 0) {
        container.innerHTML = '<div style="color: #858585;">Нет логов</div>';
        return;
    }
    
    container.innerHTML = logs.map(line => {
        let className = 'log-line';
        if (line.includes('ERROR') || line.includes('Exception')) {
            className += ' log-error';
        } else if (line.includes('WARN')) {
            className += ' log-warn';
        } else if (line.includes('INFO')) {
            className += ' log-info';
        }
        
        // Extract timestamp if present
        const timestampMatch = line.match(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/);
        if (timestampMatch) {
            const timestamp = timestampMatch[0];
            const rest = line.substring(timestamp.length);
            return `<div class="${className}"><span class="log-timestamp">${timestamp}</span>${escapeHtml(rest)}</div>`;
        }
        
        return `<div class="${className}">${escapeHtml(line)}</div>`;
    }).join('');
    
    if (autoScroll) {
        container.scrollTop = container.scrollHeight;
    }
}

// Refresh logs manually
function refreshLogs() {
    loadLogs();
}

// Clear logs view
function clearLogsView() {
    document.getElementById('logsContainer').innerHTML = '<div style="color: #858585;">Логи очищены. Обновите для загрузки новых.</div>';
    if (logsInterval) {
        clearInterval(logsInterval);
        logsInterval = null;
    }
}

// Escape HTML
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Stop logs interval when leaving logs tab
const originalSwitchTab = switchTab;
switchTab = function(tab) {
    if (tab !== 'logs' && logsInterval) {
        clearInterval(logsInterval);
        logsInterval = null;
    }
    originalSwitchTab(tab);
};


// Load system info
async function loadSystemInfo() {
    try {
        const response = await fetch(`${API_URL}/api/admin/system`, {
            headers: {
                'Authorization': `Bearer ${authToken}`
            }
        });
        
        if (response.ok) {
            const data = await response.json();
            
            // Memory
            document.getElementById('jvmMemory').textContent = formatBytes(data.usedMemory || 0);
            document.getElementById('threadCount').textContent = data.threadCount || 0;
            document.getElementById('dbConnections').textContent = data.dbConnections || 0;
            document.getElementById('wsConnections').textContent = data.wsConnections || 0;
            
            // System info
            document.getElementById('osInfo').textContent = data.osName || '-';
            document.getElementById('javaVersion').textContent = data.javaVersion || '-';
            document.getElementById('processors').textContent = data.processors || '-';
            document.getElementById('totalMemory').textContent = formatBytes(data.totalMemory || 0);
            document.getElementById('freeMemory').textContent = formatBytes(data.freeMemory || 0);
            document.getElementById('maxMemory').textContent = formatBytes(data.maxMemory || 0);
        }
    } catch (error) {
        console.error('Error loading system info:', error);
    }
}

// Load activity info
async function loadActivityInfo() {
    try {
        const response = await fetch(`${API_URL}/api/admin/activity`, {
            headers: {
                'Authorization': `Bearer ${authToken}`
            }
        });
        
        if (response.ok) {
            const data = await response.json();
            
            document.getElementById('newUsersToday').textContent = data.newUsersToday || 0;
            document.getElementById('activeUsersToday').textContent = data.activeUsersToday || 0;
            document.getElementById('messagesPerHour').textContent = data.messagesPerHour || 0;
            document.getElementById('peakOnline').textContent = data.peakOnline || 0;
            
            document.getElementById('directChats').textContent = data.directChats || 0;
            document.getElementById('groupChats').textContent = data.groupChats || 0;
            document.getElementById('channelChats').textContent = data.channelChats || 0;
            
            document.getElementById('totalCallsAll').textContent = data.totalCalls || 0;
            document.getElementById('successfulCalls').textContent = data.successfulCalls || 0;
            document.getElementById('missedCalls').textContent = data.missedCalls || 0;
            document.getElementById('avgCallDuration').textContent = formatDuration(data.avgCallDuration || 0);
            
            // Recent activity
            if (data.recentActivity && data.recentActivity.length > 0) {
                renderRecentActivity(data.recentActivity);
            }
        }
    } catch (error) {
        console.error('Error loading activity info:', error);
    }
}

// Render recent activity
function renderRecentActivity(activities) {
    const container = document.getElementById('recentActivity');
    
    container.innerHTML = activities.map(activity => `
        <div class="user-item">
            <div class="user-info">
                <div class="user-name">${activity.username} ${activity.isOnline ? '🟢' : '⚫'}</div>
                <div class="user-email">${activity.action} • ${formatTime(activity.timestamp)}</div>
            </div>
        </div>
    `).join('');
}

// Format bytes
function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

// Format duration
function formatDuration(seconds) {
    if (seconds < 60) return `${seconds}с`;
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${minutes}м ${secs}с`;
}

// Format time
function formatTime(timestamp) {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now - date;
    
    if (diff < 60000) return 'только что';
    if (diff < 3600000) return `${Math.floor(diff / 60000)} мин назад`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)} ч назад`;
    return date.toLocaleDateString('ru');
}
