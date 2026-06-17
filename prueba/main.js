// Mock Data for the demonstration
const mockData = {
  dvrs: [
    { id: 'DVR-01', name: 'Cámara Recepción', status: 'ONLINE', ip: '192.168.1.15', org: 'TechCorp' },
    { id: 'DVR-02', name: 'Almacén Central', status: 'OFFLINE', ip: '192.168.1.16', org: 'TechCorp' },
  ]
};

// UI Elements
const loginView = document.getElementById('login-view');
const registerView = document.getElementById('register-view');
const dashView = document.getElementById('dashboard-view');

const loginForm = document.getElementById('login-form');
const logoutBtn = document.getElementById('logout-btn');
const goToRegisterBtn = document.getElementById('go-to-register');
const goToLoginBtn = document.getElementById('go-to-login');

const registerOrgForm = document.getElementById('register-org-form');
const registerUserForm = document.getElementById('register-user-form');
const tabBtns = document.querySelectorAll('.tab-btn');

const navBtns = document.querySelectorAll('.nav-btn');
const pageTitle = document.getElementById('page-title');
const contentArea = document.getElementById('content-area');

// View state
let currentSection = 'dvrs';
let currentOrgUlid = null;

// Toggle Views: Login vs Register
goToRegisterBtn.addEventListener('click', (e) => {
  e.preventDefault();
  loginView.classList.remove('active');
  setTimeout(() => {
    loginView.classList.add('hidden');
    registerView.classList.remove('hidden');
    setTimeout(() => registerView.classList.add('active'), 50);
  }, 300);
});

goToLoginBtn.addEventListener('click', (e) => {
  e.preventDefault();
  registerView.classList.remove('active');
  setTimeout(() => {
    registerView.classList.add('hidden');
    loginView.classList.remove('hidden');
    setTimeout(() => loginView.classList.add('active'), 50);
  }, 300);
});

// Register Tabs Logic
tabBtns.forEach(btn => {
  btn.addEventListener('click', (e) => {
    tabBtns.forEach(b => b.classList.remove('active'));
    e.currentTarget.classList.add('active');
    
    const targetTab = e.currentTarget.dataset.tab;
    if(targetTab === 'tab-org') {
      registerUserForm.classList.add('hidden');
      registerOrgForm.classList.remove('hidden');
    } else {
      registerOrgForm.classList.add('hidden');
      registerUserForm.classList.remove('hidden');
    }
  });
});

// Mock submission for Org Registration
registerOrgForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const name = document.getElementById('reg-org-name').value;
  const email = document.getElementById('reg-org-email').value;
  const passwd = document.getElementById('reg-org-pass').value;

  const btn = registerOrgForm.querySelector('button');
  const ogText = btn.innerHTML;
  btn.innerHTML = 'Creando...';
  
  try {
    const res = await fetch('http://localhost:8080/org/create', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, email, passwd })
    });
    
    if (res.ok || res.status === 201) {
      currentOrgUlid = await res.text();
      // Auto-switch to user tab
      tabBtns[1].click();
    } else {
      const errorText = await res.text();
      alert("Error al crear organización: " + errorText);
    }
  } catch (err) {
    console.error(err);
    alert("Error de conexión");
  } finally {
    btn.innerHTML = ogText;
  }
});

// Mock submission for User Registration & Login
registerUserForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const name = document.getElementById('reg-user-name').value;
  const email = document.getElementById('reg-user-email').value;
  const passwd = document.getElementById('reg-user-pass').value;
  
  const btn = registerUserForm.querySelector('button');
  const ogText = btn.innerHTML;
  btn.innerHTML = 'Creando y entrando...';
  
  try {
    const res = await fetch('http://localhost:8080/auth/users', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, email, passwd, ulidOrg: currentOrgUlid })
    });
    
    if (res.ok || res.status === 201) {
      const token = await res.text();
      localStorage.setItem('token', token);
      
      registerView.classList.remove('active');
      setTimeout(() => {
        registerView.classList.add('hidden');
        dashView.classList.remove('hidden');
        setTimeout(() => dashView.classList.add('active'), 50);
        fetchRealDvrs();
      }, 500);
    } else {
      alert("Error al crear usuario");
    }
  } catch (err) {
    console.error(err);
    alert("Error de conexión");
  } finally {
    btn.innerHTML = ogText;
  }
});

// Login Handler
loginForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const email = document.getElementById('username').value;
  const psswd = document.getElementById('password').value;

  const btn = loginForm.querySelector('button');
  const ogText = btn.innerHTML;
  btn.innerHTML = 'Conectando...';
  
  try {
    const res = await fetch('http://localhost:8080/auth/users/login/password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, psswd })
    });
    
    if (res.ok) {
      const token = await res.text();
      localStorage.setItem('token', token);
      
      loginView.classList.remove('active');
      setTimeout(() => {
        loginView.classList.add('hidden');
        dashView.classList.remove('hidden');
        setTimeout(() => dashView.classList.add('active'), 50);
        fetchRealDvrs();
      }, 500);
    } else {
      alert("Credenciales incorrectas");
    }
  } catch (err) {
    console.error(err);
    alert("Error de conexión con el backend");
  } finally {
    btn.innerHTML = ogText;
  }
});

// Logout Handler
logoutBtn.addEventListener('click', () => {
  localStorage.removeItem('token');
  dashView.classList.remove('active');
  setTimeout(() => {
    dashView.classList.add('hidden');
    loginView.classList.remove('hidden');
    setTimeout(() => loginView.classList.add('active'), 50);
  }, 500);
});

async function fetchRealDvrs() {
  try {
    const res = await fetch('http://localhost:8080/api/dvr/list', {
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('token') }
    });
    if (res.ok) {
      const dvrs = await res.json();
      mockData.dvrs = dvrs.map(d => ({
        id: d.sipId,
        name: d.name,
        status: d.online ? 'ONLINE' : 'OFFLINE', 
        ip: d.ip || 'N/A',
        org: 'Mi Organización'
      }));
      renderContent('dvrs');
    }
  } catch (err) {
    console.error("Error al cargar lista de dvrs reales", err);
  }
}

// Navigation Handler
navBtns.forEach(btn => {
  btn.addEventListener('click', (e) => {
    navBtns.forEach(b => b.classList.remove('active'));
    const targetBtn = e.currentTarget;
    targetBtn.classList.add('active');
    
    currentSection = targetBtn.dataset.target;
    // update title text excluding the SVG icon
    pageTitle.textContent = targetBtn.textContent.trim();
    renderContent(currentSection);
  });
});

// Render dynamic content
function renderContent(section) {
  contentArea.innerHTML = ''; // clear
  const data = mockData[section];
  
  data.forEach((item, index) => {
    const card = document.createElement('div');
    card.className = 'card';
    card.style.animationDelay = `${index * 0.1}s`;
    
    if (section === 'dvrs') {
      innerHTML = `
        <div class="card-header">
          <div class="card-title">${item.name}</div>
          <div class="badge ${item.status.toLowerCase()}">${item.status}</div>
        </div>
        <div class="card-body">
          <p><strong>ID:</strong> ${item.id}</p>
          <p><strong>IP:</strong> ${item.ip}</p>
          <p><strong>Organización:</strong> ${item.org}</p>
        </div>
        <div class="card-actions">
          <button class="btn primary-btn small play-btn">Ver Stream</button>
          <button class="btn outline-btn small">Configurar</button>
        </div>
      `;
    }
    
    card.innerHTML = innerHTML;
    
    // Bind "Ver Stream" event
    if (section === 'dvrs') {
      const playBtn = card.querySelector('.play-btn');
      playBtn.addEventListener('click', () => {
        openVideoModal(item);
      });
    }

    contentArea.appendChild(card);
  });
}

// ==========================================
// Video Player & WVP Integration Logic
// ==========================================
let flvPlayer = null;
const videoModal = document.getElementById('video-modal');
const videoElement = document.getElementById('dvr-video-player');
const closeVideoModal = document.getElementById('close-video-modal');
const videoModalTitle = document.getElementById('video-modal-title');
const videoLoading = document.getElementById('video-loading');

async function openVideoModal(dvr) {
  videoModal.classList.remove('hidden');
  videoModalTitle.textContent = `Viendo: ${dvr.name}`;
  videoLoading.classList.remove('hidden');
  videoLoading.textContent = "Conectando al servidor WVP y obteniendo link TCP...";
  videoElement.classList.add('hidden');

  try {
    // Apuntamos directo a nuestro backend de Spring Boot (que hará el proxy hacia WVP)
    const playUrl = `/api/dvr/play/${dvr.id}`;
    console.log("Pidiendo stream a nuestro proxy Java:", playUrl);
    
    const response = await fetch(`http://localhost:8080/api/dvr/play/${dvr.id}`, {
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('token') }
    });
    const result = await response.json();

    // WVP suele devolver el link FLV dentro de result.data.flv o result.data.ws_flv
    if (result.code === 0 && result.data) {
      // Priorizamos WebSocket FLV si existe, si no, HTTP FLV
      const flvUrl = result.data.ws_flv || result.data.flv; 
      
      if (!flvUrl) throw new Error("WVP no devolvió un link FLV válido");

      videoLoading.classList.add('hidden');
      videoElement.classList.remove('hidden');
      
      if (typeof flvjs !== 'undefined' && flvjs.isSupported()) {
        flvPlayer = flvjs.createPlayer({
          type: 'flv',
          url: flvUrl,
          isLive: true
        });
        flvPlayer.attachMediaElement(videoElement);
        flvPlayer.load();
        flvPlayer.play();
      } else {
        alert('Tu navegador no soporta FLV.js');
      }
    } else {
      throw new Error(result.msg || "Error desconocido en WVP");
    }
  } catch (error) {
    videoLoading.textContent = "Error al obtener el video: " + error.message;
    console.error("WVP Play Error:", error);
  }
}

closeVideoModal.addEventListener('click', () => {
  if (flvPlayer) {
    flvPlayer.destroy();
    flvPlayer = null;
  }
  videoModal.classList.add('hidden');
});

// ==========================================
// Form Add New DVR
// ==========================================
const addDvrModal = document.getElementById('add-dvr-modal');
const closeAddModal = document.getElementById('close-add-modal');
const addNewBtn = document.getElementById('add-new-btn');
const addDvrForm = document.getElementById('add-dvr-form');

addNewBtn.addEventListener('click', () => {
  addDvrModal.classList.remove('hidden');
});

closeAddModal.addEventListener('click', () => {
  addDvrModal.classList.add('hidden');
});

addDvrForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const sipId = document.getElementById('new-dvr-id').value;
  const name = document.getElementById('new-dvr-name').value;
  
  const btn = addDvrForm.querySelector('button');
  const originalText = btn.innerHTML;
  btn.innerHTML = 'Guardando...';

  try {
    // Apuntamos al proxy de Java. No tenemos CORS configurado así que pasamos la ruta relativa
    // (Asegúrate de que vite.config.js tenga el proxy o el backend Spring Boot permita CORS si lo haces directo)
    const response = await fetch('http://localhost:8080/api/dvr/register', {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + localStorage.getItem('token')
      },
      body: JSON.stringify({ sipId: sipId, name: name })
    });
    
    if (response.ok || response.status === 201) {
      // Actualizamos mockData local
      mockData.dvrs.push({
        id: sipId,
        name: name,
        status: 'OFFLINE', // Asumimos Offline al registrar, el SSE luego lo actualiza a ONLINE
        ip: 'Pendiente',
        org: 'Mi Organización'
      });
      
      renderContent('dvrs');
      addDvrModal.classList.add('hidden');
      addDvrForm.reset();
    } else {
      alert("Error al registrar DVR en la base de datos.");
    }
  } catch (err) {
    console.error(err);
    alert("No se pudo conectar con el servidor Spring Boot (Revisa CORS o Proxy)");
  } finally {
    btn.innerHTML = originalText;
  }
});
