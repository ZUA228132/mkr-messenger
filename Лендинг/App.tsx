
import React, { useState, useEffect, useRef } from 'react';
import { 
  ShieldCheck, 
  Lock, 
  Globe, 
  Download,
  Smartphone,
  Menu,
  X,
  ArrowRight,
  Activity,
  Send,
  Zap,
  Fingerprint,
  ShieldAlert,
  Server,
  EyeOff,
  QrCode,
  Laptop,
  Briefcase,
  UserCheck,
  Building2
} from 'lucide-react';
import { generatePioneerResponse } from './geminiService';
import { Message } from './types';

const RuStoreLogo = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path d="M12 2L4 5V11C4 16.5 12 22 12 22C12 22 20 16.5 20 11V5L12 2Z" fill="#005BFF" />
    <path d="M16 11L12 15L8 11M12 15V7" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
  </svg>
);

const App: React.FC = () => {
  const [messages, setMessages] = useState<Message[]>([
    { id: '1', sender: 'pioneer-ai', text: 'Протокол безопасности ПИОНЕР активен. Ожидаю ваши указания.', timestamp: new Date() }
  ]);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isWebLoginOpen, setIsWebLoginOpen] = useState(false);
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 });
  const chatEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      setMousePos({
        x: (e.clientX / window.innerWidth - 0.5) * 4,
        y: (e.clientY / window.innerHeight - 0.5) * 4,
      });
    };
    window.addEventListener('mousemove', handleMouseMove);
    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) entry.target.classList.add('active');
      });
    }, { threshold: 0.1 });
    document.querySelectorAll('.reveal-item').forEach(el => observer.observe(el));
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      observer.disconnect();
    };
  }, []);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim()) return;
    const userMsg: Message = { id: Date.now().toString(), sender: 'user', text: input, timestamp: new Date() };
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setIsTyping(true);
    const aiResponse = await generatePioneerResponse(input);
    const aiMsg: Message = { id: (Date.now() + 1).toString(), sender: 'pioneer-ai', text: aiResponse || 'Связь защищена.', timestamp: new Date() };
    setMessages(prev => [...prev, aiMsg]);
    setIsTyping(false);
  };

  return (
    <div className="min-h-screen text-white">
      {/* Web Login Modal */}
      {isWebLoginOpen && (
        <div className="fixed inset-0 z-[300] flex items-center justify-center px-6">
          <div className="absolute inset-0 bg-black/80 backdrop-blur-xl" onClick={() => setIsWebLoginOpen(false)}></div>
          <div className="relative glass w-full max-w-2xl rounded-[3rem] p-10 md:p-16 overflow-hidden animate-up">
            <button 
              className="absolute top-8 right-8 text-white/40 hover:text-white transition-colors"
              onClick={() => setIsWebLoginOpen(false)}
            >
              <X size={32} />
            </button>

            <div className="text-center mb-12">
              <div className="w-16 h-16 bg-white/5 rounded-2xl flex items-center justify-center mx-auto mb-6">
                <Laptop className="text-rose-500 w-8 h-8" />
              </div>
              <h2 className="text-3xl md:text-4xl font-display font-black uppercase tracking-tight mb-4">Вход в WEB-версию</h2>
              <p className="text-white/50 text-lg font-light">Синхронизируйте ваше устройство за несколько секунд</p>
            </div>

            <div className="grid md:grid-cols-2 gap-12 items-center">
              <div className="space-y-8">
                <div className="flex gap-5">
                  <div className="w-10 h-10 rounded-full bg-rose-600/20 border border-rose-500/20 flex items-center justify-center text-rose-500 font-black flex-shrink-0">1</div>
                  <p className="text-white/80 leading-relaxed font-medium">Откройте <span className="text-white font-bold tracking-widest">ПИОНЕР</span> на вашем смартфоне</p>
                </div>
                <div className="flex gap-5">
                  <div className="w-10 h-10 rounded-full bg-rose-600/20 border border-rose-500/20 flex items-center justify-center text-rose-500 font-black flex-shrink-0">2</div>
                  <p className="text-white/80 leading-relaxed font-medium">Перейдите в <span className="text-white font-bold">Настройки</span> &gt; <span className="text-white font-bold">Связанные устройства</span></p>
                </div>
                <div className="flex gap-5">
                  <div className="w-10 h-10 rounded-full bg-rose-600/20 border border-rose-500/20 flex items-center justify-center text-rose-500 font-black flex-shrink-0">3</div>
                  <p className="text-white/80 leading-relaxed font-medium">Нажмите <span className="text-rose-500 font-bold">Привязать устройство</span> и отсканируйте код</p>
                </div>
              </div>

              <div className="flex justify-center">
                <div className="relative p-6 bg-white rounded-[2rem] shadow-[0_0_50px_rgba(255,255,255,0.1)] group">
                  <div className="w-48 h-48 md:w-56 md:h-56 bg-white flex items-center justify-center">
                    <div className="relative">
                      <QrCode size={200} className="text-black" strokeWidth={1} />
                      <div className="absolute inset-0 flex items-center justify-center">
                         <div className="w-12 h-12 bg-black rounded-xl flex items-center justify-center border-4 border-white">
                            <ShieldCheck className="text-rose-500 w-6 h-6" />
                         </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            
            <div className="mt-16 pt-8 border-t border-white/5 text-center">
               <p className="text-[10px] font-black uppercase tracking-[0.4em] text-white/20">Безопасное соединение по протоколу P2P-SEC</p>
            </div>
          </div>
        </div>
      )}

      {/* Navigation */}
      <nav className="fixed top-0 left-0 w-full z-[200] p-4 md:p-6">
        <div className="max-w-6xl mx-auto glass rounded-3xl px-6 md:px-10 h-16 md:h-18 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-gradient-to-br from-rose-500 to-purple-600 rounded-lg flex items-center justify-center shadow-lg shadow-rose-500/20">
              <ShieldCheck className="text-white w-5 h-5" />
            </div>
            <span className="text-lg font-display font-black tracking-tighter uppercase">ПИОНЕР</span>
          </div>

          <div className="hidden lg:flex items-center gap-8">
            {['Технологии', 'Защита', 'Ноды'].map(i => (
              <a key={i} href="#" className="text-[9px] font-black uppercase tracking-[0.3em] text-white/40 hover:text-white transition-all">{i}</a>
            ))}
          </div>

          <div className="flex items-center gap-3">
            <button className="hidden sm:block text-[9px] font-black uppercase tracking-[0.2em] text-white/50 px-3 hover:text-white transition-colors">RU</button>
            <button className="bg-white text-black text-[10px] font-black px-6 py-3 rounded-full uppercase tracking-widest hover:scale-105 active:scale-95 transition-all">Вход</button>
            <button className="lg:hidden text-white p-2" onClick={() => setIsMenuOpen(!isMenuOpen)}>
              {isMenuOpen ? <X size={20} /> : <Menu size={20} />}
            </button>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="pt-28 md:pt-40 pb-16 px-6 overflow-hidden">
        <div className="max-w-7xl mx-auto flex flex-col lg:flex-row items-center gap-12 lg:gap-20">
          
          <div className="flex-1 text-left z-10 w-full">
            <div className="reveal-item inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-white/5 border border-white/10 mb-8 backdrop-blur-md">
              <span className="w-1.5 h-1.5 bg-rose-500 rounded-full animate-pulse"></span>
              <span className="text-[9px] font-black text-white/70 uppercase tracking-[0.3em]">Protocol 7.0 Alpha</span>
            </div>
            
            <div className="mb-10 md:mb-12">
              <h1 className="reveal-item font-display font-black leading-[1.1] md:leading-[1.0] tracking-tight uppercase flex flex-col">
                <span className="text-4xl sm:text-6xl md:text-7xl lg:text-8xl bg-gradient-to-r from-white to-white/60 bg-clip-text text-transparent">Абсолютная</span>
                <span className="text-4xl sm:text-6xl md:text-7xl lg:text-8xl italic bg-gradient-to-r from-rose-400 via-rose-500 to-purple-600 bg-clip-text text-transparent py-1">тишина</span>
                <span className="text-4xl sm:text-6xl md:text-7xl lg:text-8xl text-white">цифрового</span>
                <span className="text-4xl sm:text-6xl md:text-7xl lg:text-8xl text-white/40">мира.</span>
              </h1>
            </div>
            
            <p className="reveal-item text-lg md:text-xl text-white/60 mb-12 max-w-xl leading-relaxed font-light">
              Слепой сервер-транзит, который не видит ваших данных. Полная анонимность и безопасность высшего уровня.
            </p>
            
            <div className="reveal-item flex flex-col gap-5 w-full max-w-md">
              {/* Web Login Button moved here */}
              <a 
                href="/web/"
                className="w-full flex items-center justify-center gap-4 py-6 rounded-2xl glass border-rose-500/30 hover:border-rose-500/60 transition-all group shadow-2xl shadow-rose-900/10 no-underline text-white"
              >
                <div className="w-12 h-12 bg-rose-500/10 rounded-xl flex items-center justify-center group-hover:bg-rose-500 transition-all">
                  <Laptop className="w-6 h-6 text-white" />
                </div>
                <div className="text-left">
                  <div className="text-[10px] uppercase font-black text-white/40 tracking-[0.2em] mb-0.5">Desktop</div>
                  <div className="text-lg font-black uppercase tracking-tight">Открыть WEB-версию</div>
                </div>
              </a>

              <div className="flex flex-col sm:flex-row items-center justify-start gap-4">
                <a href="/pioneer.apk" download className="w-full sm:w-1/2 btn-premium px-6 py-5 rounded-2xl flex items-center justify-center gap-4 group no-underline text-white">
                  <Download className="w-5 h-5 text-white" />
                  <div className="text-left">
                    <div className="text-[9px] uppercase font-black text-white/30 tracking-widest mb-0.5">Mobile</div>
                    <div className="text-base font-bold uppercase">Android APK</div>
                  </div>
                </a>

                <a href="https://www.rustore.ru/" target="_blank" rel="noopener noreferrer" className="w-full sm:w-1/2 btn-premium px-6 py-5 rounded-2xl flex items-center justify-center gap-4 group border-blue-500/20 no-underline text-white">
                  <RuStoreLogo />
                  <div className="text-left">
                    <div className="text-[9px] uppercase font-black text-white/30 tracking-widest mb-0.5">Store</div>
                    <div className="text-base font-bold uppercase">RuStore</div>
                  </div>
                </a>
              </div>
            </div>
          </div>

          <div className="reveal-item flex-1 relative w-full hidden lg:block" style={{ transform: `rotateY(${mousePos.x}deg) rotateX(${-mousePos.y}deg)` }}>
            <div className="w-[300px] h-[620px] bg-black rounded-[3rem] border-[10px] border-[#1a1a1a] shadow-[0_50px_100px_-20px_rgba(0,0,0,0.8)] mx-auto overflow-hidden relative ring-1 ring-white/10 iphone-float">
              <div className="absolute top-4 left-1/2 -translate-x-1/2 w-24 h-6 bg-black rounded-2xl z-50"></div>
              <div className="p-8 pt-16 h-full bg-[#050505] flex flex-col">
                <div className="flex items-center gap-3 mb-10">
                  <div className="w-10 h-10 bg-rose-500 rounded-xl"></div>
                  <div className="h-2 w-20 bg-white/10 rounded-full"></div>
                </div>
                <div className="space-y-4">
                  <div className="h-16 w-full glass rounded-2xl"></div>
                  <div className="h-16 w-3/4 glass rounded-2xl"></div>
                  <div className="h-16 w-full glass rounded-2xl opacity-50"></div>
                </div>
              </div>
            </div>
            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[500px] bg-rose-600/5 blur-[120px] -z-10 rounded-full"></div>
          </div>

        </div>
      </section>

      {/* Target Audience Section */}
      <section className="py-24 px-6 max-w-7xl mx-auto">
        <div className="text-center mb-20">
          <div className="reveal-item inline-block text-[11px] font-black text-rose-500 uppercase tracking-[0.5em] mb-4">Target Audience</div>
          <h2 className="reveal-item text-4xl md:text-6xl font-display font-black uppercase tracking-tight">Для кого создан ПИОНЕР?</h2>
        </div>
        
        <div className="grid md:grid-cols-3 gap-8">
          {[
            {
              icon: <Building2 className="w-8 h-8 text-rose-500" />,
              title: "Госструктуры",
              desc: "Закрытый контур для передачи стратегически важных данных без риска перехвата зарубежными сервисами."
            },
            {
              icon: <Briefcase className="w-8 h-8 text-blue-500" />,
              title: "Бизнес-элита",
              desc: "Защита коммерческой тайны, финансовых транзакций и конфиденциальных переговоров на высшем уровне."
            },
            {
              icon: <UserCheck className="w-8 h-8 text-purple-500" />,
              title: "Приватный сектор",
              desc: "Для тех, кто ценит цифровую гигиену и не желает оставлять следов своей активности в сети."
            }
          ].map((item, idx) => (
            <div key={idx} className="reveal-item group glass p-10 rounded-[2.5rem] hover:bg-white/[0.06] transition-all border-white/5 hover:border-white/10">
              <div className="w-16 h-16 bg-white/5 rounded-2xl flex items-center justify-center mb-8 group-hover:scale-110 transition-transform duration-500">
                {item.icon}
              </div>
              <h3 className="text-2xl font-black uppercase tracking-tight mb-4">{item.title}</h3>
              <p className="text-white/50 leading-relaxed font-light">{item.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Bento Grid */}
      <section className="py-20 md:py-32 px-6 max-w-7xl mx-auto">
        <div className="grid md:grid-cols-3 gap-6">
          <div className="reveal-item md:col-span-2 glass p-10 md:p-16 rounded-[3rem] relative overflow-hidden group">
            <EyeOff className="text-blue-500 w-10 h-10 mb-8" />
            <h3 className="text-3xl md:text-5xl font-display font-black mb-6 uppercase tracking-tight">Слепой сервер.</h3>
            <p className="text-white/50 text-lg md:text-xl font-light leading-relaxed max-w-2xl">
              Мы не храним ключи. Мы не видим участников. Мы обеспечиваем только чистый транспорт для ваших зашифрованных данных.
            </p>
            <div className="absolute top-0 right-0 w-64 h-64 bg-blue-600/5 blur-[100px] rounded-full"></div>
          </div>

          <div className="reveal-item glass p-10 rounded-[3rem] flex flex-col justify-between border-rose-500/10">
            <div>
              <Server className="text-rose-500 w-10 h-10 mb-8" />
              <h3 className="text-2xl font-display font-black mb-4 uppercase tracking-tight">Zero-Knowledge</h3>
              <p className="text-white/40 text-base leading-relaxed">
                Инфраструктура, где приватность заложена в архитектуру кода, а не в обещания.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Chat Section */}
      <section className="py-20 md:py-32 px-6">
        <div className="max-w-4xl mx-auto">
          <div className="reveal-item glass rounded-[3rem] overflow-hidden flex flex-col h-[600px] md:h-[700px] shadow-2xl">
            <div className="p-8 border-b border-white/5 flex items-center justify-between bg-white/[0.01]">
              <div className="flex items-center gap-4">
                <div className="w-10 h-10 rounded-xl bg-rose-600 flex items-center justify-center">
                  <ShieldCheck className="w-5 h-5 text-white" />
                </div>
                <div>
                  <div className="text-sm font-black tracking-widest uppercase">Pioneer AI</div>
                  <div className="text-[9px] text-rose-500 font-black uppercase tracking-widest">Active</div>
                </div>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-6 md:p-10 space-y-8 scrollbar-hide">
              {messages.map((m) => (
                <div key={m.id} className={`flex ${m.sender === 'user' ? 'justify-end' : 'justify-start'}`}>
                  <div className={`max-w-[85%] p-6 rounded-3xl text-sm md:text-base leading-relaxed ${
                    m.sender === 'user' 
                      ? 'bg-rose-600 text-white rounded-tr-none' 
                      : 'bg-white/5 text-white/80 border border-white/5 rounded-tl-none'
                  }`}>
                    {m.text}
                  </div>
                </div>
              ))}
              {isTyping && (
                <div className="flex justify-start">
                  <div className="bg-white/5 px-6 py-4 rounded-full flex gap-2">
                    <div className="w-1 h-1 bg-rose-600 rounded-full animate-bounce"></div>
                    <div className="w-1 h-1 bg-rose-600 rounded-full animate-bounce delay-75"></div>
                    <div className="w-1 h-1 bg-rose-600 rounded-full animate-bounce delay-150"></div>
                  </div>
                </div>
              )}
              <div ref={chatEndRef} />
            </div>

            <form onSubmit={handleSendMessage} className="p-6 md:p-8 bg-black/40">
              <div className="relative">
                <input
                  type="text"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  placeholder="Запрос в систему..."
                  className="w-full bg-white/5 border border-white/10 py-5 px-8 rounded-full focus:outline-none focus:border-rose-600/40 transition-all font-medium text-sm placeholder:text-white/20"
                />
                <button 
                  type="submit" 
                  disabled={!input.trim()}
                  className="absolute right-2 top-1/2 -translate-y-1/2 w-12 h-12 bg-white text-black rounded-full flex items-center justify-center hover:scale-105 active:scale-95 transition-all"
                >
                  <Send size={18} />
                </button>
              </div>
            </form>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="py-20 border-t border-white/5 bg-[#010101] px-6">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-center gap-12 text-center md:text-left">
          <div>
            <div className="flex items-center gap-3 mb-6 justify-center md:justify-start">
              <ShieldAlert className="text-rose-500 w-8 h-8" />
              <span className="text-2xl font-display font-black tracking-tighter uppercase">ПИОНЕР</span>
            </div>
            <p className="text-white/30 text-sm max-w-xs font-light">
              Система защищенной связи нового поколения.
            </p>
          </div>
          <div className="flex gap-4">
            <button className="glass px-6 py-3 rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-white/10 transition-colors">Documentation</button>
            <button className="glass px-6 py-3 rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-white/10 transition-colors">Support</button>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default App;
