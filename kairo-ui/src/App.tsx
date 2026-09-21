import { BrowserRouter as Router, Routes, Route, Link } from "react-router-dom"
import { LayoutDashboard } from "lucide-react"
import { WorkflowList } from "./pages/WorkflowList"
import { WorkflowDetail } from "./pages/WorkflowDetail"
import { useEffect } from "react"

function App() {
  // Force dark mode for that premium feel
  useEffect(() => {
    document.documentElement.classList.add('dark')
  }, [])

  return (
    <Router>
      <div className="min-h-screen bg-background text-foreground flex flex-col font-sans relative overflow-hidden">
        {/* Subtle Background Glow Orb */}
        <div className="bg-glow top-0 left-1/2 -translate-x-1/2 -translate-y-1/2 opacity-50"></div>

        {/* Top Navbar */}
        <header className="sticky top-0 z-50 border-b border-border/40 bg-background/60 backdrop-blur-md">
          <div className="container flex h-16 max-w-screen-2xl items-center px-6">
            <Link to="/" className="flex items-center space-x-3 mr-6 text-primary group">
              <div className="p-2 rounded-xl bg-primary/10 group-hover:bg-primary/20 transition-colors flex items-center justify-center">
                <img src="/logo-1.png" alt="Kairo Logo" className="h-6 w-6 object-contain rounded-sm" />
              </div>
              <span className="font-semibold tracking-tight text-lg hidden sm:inline-block">
                Kairo <span className="text-muted-foreground font-normal">Orchestrator</span>
              </span>
            </Link>
          </div>
        </header>

        {/* Main Content */}
        <main className="flex-1 container max-w-screen-2xl py-8 px-6 mx-auto relative z-10">
          <Routes>
            <Route path="/" element={<WorkflowList />} />
            <Route path="/workflows/:id" element={<WorkflowDetail />} />
          </Routes>
        </main>
      </div>
    </Router>
  )
}

export default App
