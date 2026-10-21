import { useEffect } from "react";
import { Contact, Footer } from "@/components/contact";
import { Header } from "@/components/header";
import { Hero } from "@/components/hero";
import { QuoteDesk, Rates, TrackDesk } from "@/components/quote-desk";
import { About, Materials, Plants, Stats } from "@/components/story";
import { useDesk } from "@/store/desk";

export function Site() {
  useEffect(() => {
    void useDesk.persist.rehydrate();
  }, []);

  return (
    <>
      <Header />
      <main>
        <Hero />
        <Stats />
        <About />
        <Materials />
        <Plants />
        <Rates />
        <QuoteDesk />
        <TrackDesk />
        <Contact />
      </main>
      <Footer />
    </>
  );
}
