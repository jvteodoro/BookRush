import { useState } from "react";
import { createRoot } from "react-dom/client";
import { ThemeProvider } from "@bookrush/ui/theme";
import { Button } from "../../src/components/ui/Button";
import { BookDetailsDrawer } from "../../src/reader/BookDetailsDrawer";
import { AppShell } from "../../src/components/layout/AppShell";
import { BookRow } from "../../src/components/book/BookRow";
import { authClient } from "../../src/auth";
import "@bookrush/ui/styles.css";
import "@bookrush/ui/sidebar.css";
import "@bookrush/ui/layout.css";
import "../../src/styles/reader-shell.css";
import "../../src/styles/ratings.css";
import "../../src/styles/book-details.css";

// Local visual fixture only: no identity session or production requests.
let myRating: number | null = null;
authClient.fetch = async (url, init) => {
  if (url.includes("/ratings")) {
    if (init?.method === "PUT") myRating = JSON.parse(String(init.body)).stars;
    if (init?.method === "DELETE") myRating = null;
    return Response.json({
      bookId: "dom-casmurro",
      totalRatings: 128 + (myRating ? 1 : 0),
      averageRating: 4.7,
      myRating,
    });
  }
  if (url.includes("/seen")) return Response.json({ items: [] });
  return new Response(null, { status: 404 });
};
const book = {
  id: "dom-casmurro",
  title: "Dom Casmurro",
  author: "Machado de Assis",
  genre: "Romance",
  language: "pt",
  categories: [{ id: "romance", name: "Romance", scheme: "GENRE" }],
  description:
    "Entre lembranças e silêncios, Bentinho revisita a juventude no Rio de Janeiro e sua história com Capitu. Uma amizade de infância, um amor que parecia para sempre e uma dúvida que atravessa os anos.\n\nCom a ironia e a delicadeza de Machado de Assis, Dom Casmurro convida você a descobrir o que existe entre os fatos e a forma como escolhemos lembrá-los.",
  progress: 0,
  minutes: 0,
};
function Preview() {
  const [open, setOpen] = useState(true);
  const [saved, setSaved] = useState(false);
  const [message, setMessage] = useState("");
  return (
    <ThemeProvider>
      <AppShell
        active="recommendations"
        query=""
        onSearch={() => {}}
        onNavigate={() => {}}
        userName="Ana"
      >
        <p className="br-muted">Prévia visual · dados demonstrativos</p>
        <h1>Sua próxima história está aqui.</h1>
        <BookRow
          title="Escolhidos para você"
          books={[book]}
          onOpen={() => setOpen(true)}
        />
        <Button onClick={() => setOpen(true)}>Ver livro</Button>
        <p role="status">{message}</p>
        {open && (
          <BookDetailsDrawer
            book={book}
            saved={saved}
            onSave={() => setSaved((value) => !value)}
            onRead={() =>
              setMessage("Na aplicação, este botão abre o leitor do livro.")
            }
            onClose={() => setOpen(false)}
          />
        )}
      </AppShell>
    </ThemeProvider>
  );
}
createRoot(document.getElementById("root")!).render(<Preview />);
