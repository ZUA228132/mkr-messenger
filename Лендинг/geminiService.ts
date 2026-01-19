
import { GoogleGenAI } from "@google/genai";

const ai = new GoogleGenAI({ apiKey: process.env.API_KEY });

export const generatePioneerResponse = async (userMessage: string) => {
  try {
    const response = await ai.models.generateContent({
      model: 'gemini-3-flash-preview',
      contents: userMessage,
      config: {
        systemInstruction: "Вы — ПИОНЕР ИИ, интеллектуальный интерфейс премиальной системы защищенной связи 'ПИОНЕР'. Ваш тон: изысканный, вежливый, профессиональный и уверенный. Вы общаетесь с топ-менеджментом и государственными деятелями. Ваши ответы должны быть глубокими, но лаконичными. Подчеркивайте надежность, эстетику и технологическое превосходство платформы. Избегайте лишнего технического шума, если об этом не просят напрямую. Вы — лицо безопасности высшего уровня.",
        temperature: 0.4,
      },
    });
    return response.text;
  } catch (error) {
    console.error("Критическая ошибка ПИОНЕР:", error);
    return "Извините, сейчас я не могу обработать ваш запрос. Пожалуйста, обратитесь в службу поддержки.";
  }
};
