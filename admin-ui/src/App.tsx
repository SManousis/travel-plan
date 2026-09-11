type Section = {
  title: string;
  description: string;
  icon: "people" | "route" | "card";
};

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

          <div className="card-grid">
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
