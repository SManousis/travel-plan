import * as React from "react";

type Section = {
  title: string;
  description: string;
  icon: "people" | "route" | "card";
};

type Resource = { id?: string; email?: string; name?: string; country?: string };

const sections: Section[] = [
  {
    title: "Users and travelers",
    description: "Keep traveler profiles and account details organized.",
    icon: "people",
  },
  {
    title: "Travels and itineraries",
    description: "Bring trips, schedules, and travel details into view.",
    icon: "route",
  },
  {
    title: "Payment methods",
    description: "Maintain the payment options used across travel plans.",
    icon: "card",
  },
];

function CompassMark() {
  return (
    <svg aria-hidden="true" viewBox="0 0 32 32">
      <circle cx="16" cy="16" r="12.5" />
      <path d="m20.8 10.8-2.9 7.1-7.1 2.9 2.9-7.1 7.1-2.9Z" />
      <circle cx="16" cy="16" r="1.35" className="mark-dot" />
    </svg>
  );
}

function SectionIcon({ icon }: Pick<Section, "icon">) {
  if (icon === "people") {
    return (
      <svg aria-hidden="true" viewBox="0 0 24 24">
        <path d="M9.5 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z" />
        <path d="M3.5 20a6 6 0 0 1 12 0" />
        <path d="M16 8a2.75 2.75 0 0 1 0 5.5M17.5 16a4.5 4.5 0 0 1 3 4" />
      </svg>
    );
  }

  if (icon === "route") {
    return (
      <svg aria-hidden="true" viewBox="0 0 24 24">
        <circle cx="6" cy="18" r="2.5" />
        <circle cx="18" cy="6" r="2.5" />
        <path d="M8.5 18h2.25a2 2 0 0 0 2-2v-1.5a2 2 0 0 0-2-2H9a2 2 0 0 1-2-2V9a3 3 0 0 1 3-3h5.5" />
      </svg>
    );
  }

  return (
    <svg aria-hidden="true" viewBox="0 0 24 24">
      <rect x="3" y="5" width="18" height="14" rx="2.5" />
      <path d="M3 10h18M7 15h3" />
    </svg>
  );
}

function App() {
  const [token, setToken] = React.useState(() => sessionStorage.getItem("travelPlanToken"));
  const [username, setUsername] = React.useState("admin");
  const [password, setPassword] = React.useState("");
  const [error, setError] = React.useState("");
  const [users, setUsers] = React.useState<Resource[]>([]);
  const [travels, setTravels] = React.useState<Resource[]>([]);
  const [section, setSection] = React.useState("Users and travelers");
  const [draft, setDraft] = React.useState({email:"",firstName:"",lastName:""});
  const [busy, setBusy] = React.useState(false);
  async function createUser(event: React.FormEvent) { event.preventDefault(); setBusy(true); setError(""); try { const response=await fetch("/api/users",{method:"POST",headers:{"Content-Type":"application/json",Authorization:`Bearer ${token}`},body:JSON.stringify({...draft,role:"OPERATOR"})}); if(response.status===401){setToken(null);return;} if(!response.ok){setError("Unable to create user");return;} setDraft({email:"",firstName:"",lastName:""}); const refreshed=await fetch("/api/users",{headers:{Authorization:`Bearer ${token}`}}); setUsers((await refreshed.json()).items??[]); } finally {setBusy(false);} }
  async function login(event: React.FormEvent) { event.preventDefault(); setError(""); const response = await fetch("/api/auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ username, password }) }); if (!response.ok) { setError("Invalid administrator credentials"); return; } const data = await response.json(); sessionStorage.setItem("travelPlanToken", data.accessToken); setToken(data.accessToken); }
  React.useEffect(() => { if (!token) return; const headers = { Authorization: `Bearer ${token}` }; Promise.all([fetch("/api/users", { headers }), fetch("/api/travels", { headers })]).then(async ([u, t]) => { if (u.status === 401 || t.status === 401) { sessionStorage.removeItem("travelPlanToken"); setToken(null); return; } setUsers((await u.json()).items ?? []); setTravels((await t.json()).items ?? []); }).catch(() => setError("The administration services are unavailable")); }, [token]);
  if (!token) return <main className="login-shell"><form className="login-card" onSubmit={login}><p className="eyebrow">Travel Plan Admin</p><h1>Sign in</h1><label>Username<input value={username} onChange={(e) => setUsername(e.target.value)} /></label><label>Password<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>{error && <p role="alert">{error}</p>}<button type="submit">Sign in securely</button></form></main>;
  return (
    <div className="app-shell">
      <header className="site-header">
        <a className="brand" href="#main-content" aria-label="Travel Plan Admin home">
          <span className="brand-mark">
            <CompassMark />
          </span>
          <span>Travel Plan Admin</span>
        </a>
        <span className="phase-label">Admin workspace</span>
      </header>

      <main id="main-content">
        <section className="hero" aria-labelledby="welcome-title">
          <div className="hero-copy">
            <p className="eyebrow">Welcome aboard</p>
            <h1 id="welcome-title">Travel operations, clearly organized.</h1>
            <p className="intro">Manage your travel operations in one place.</p>
          </div>
          <div className="journey-line" aria-hidden="true">
            <span />
            <svg viewBox="0 0 64 24">
              <path d="M2 20C19 20 18 4 36 4h26" />
              <path d="m56 1 6 3-6 3" />
            </svg>
          </div>
        </section>

        <section className="capabilities" aria-labelledby="capabilities-title">
          <div className="section-heading">
            <p className="eyebrow">Workspace</p>
            <h2 id="capabilities-title">Everything your team needs to coordinate travel</h2>
          </div>

          <nav aria-label="Admin sections" className="section-nav">{sections.map(s=><button key={s.title} className={section===s.title?"active":""} onClick={()=>setSection(s.title)}>{s.title}</button>)}</nav><div className="card-grid">
            {sections.map((section, index) => (
              <article className="capability-card" key={section.title}>
                <div className="card-topline">
                  <span className="icon-wrap">
                    <SectionIcon icon={section.icon} />
                  </span>
                  <span className="card-index">0{index + 1}</span>
                </div>
                <h3>{section.title}</h3>
                <p>{section.description}</p>
              </article>
            ))}
          </div>
          <div className="data-summary"><h2>{section}</h2><p>{users.length} users · {travels.length} travels</p>{section==="Users and travelers"&&<form onSubmit={createUser} className="quick-form"><label>Email<input required type="email" value={draft.email} onChange={e=>setDraft({...draft,email:e.target.value})}/></label><label>First name<input required value={draft.firstName} onChange={e=>setDraft({...draft,firstName:e.target.value})}/></label><label>Last name<input required value={draft.lastName} onChange={e=>setDraft({...draft,lastName:e.target.value})}/></label><button disabled={busy}>{busy?"Saving…":"Create user"}</button></form>}<button onClick={() => { sessionStorage.removeItem("travelPlanToken"); setToken(null); }}>Sign out</button></div>
        </section>
      </main>

      <footer>
        <span>Travel Plan</span>
        <span>Operations administration</span>
      </footer>
    </div>
  );
}

export default App;
