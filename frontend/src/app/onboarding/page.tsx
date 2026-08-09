"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import type { Gender, ProfileResponse } from "@/lib/types";

/** 위치 권한을 거부하거나 못 받을 때 쓰는 기본 좌표(서울시청). 위치가 없으면 피드가 빈다. */
const FALLBACK = { lat: 37.5665, lng: 126.978 };

export default function OnboardingPage() {
  const router = useRouter();
  const [checking, setChecking] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);

  const [gender, setGender] = useState<Gender>("MALE");
  const [birthday, setBirthday] = useState("1995-01-01");
  const [bio, setBio] = useState("안녕하세요! 잘 부탁드립니다.");
  const [tags, setTags] = useState("러닝, 여행");
  const [prefGender, setPrefGender] = useState<Gender>("FEMALE");
  const [prefAgeMin, setPrefAgeMin] = useState(20);
  const [prefAgeMax, setPrefAgeMax] = useState(40);
  const [prefDistanceKm, setPrefDistanceKm] = useState(10);

  // 이미 프로필이 있으면 온보딩을 건너뛴다.
  useEffect(() => {
    api<ProfileResponse[]>("/api/profiles/me")
      .then((profiles) => {
        if (profiles.length > 0) router.replace("/feed");
        else setChecking(false);
      })
      .catch(() => setChecking(false));
  }, [router]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      setStatus("프로필 만드는 중…");
      const profileId = await api<string>("/api/profiles", {
        method: "POST",
        body: {
          gender,
          birthday: birthday.replaceAll("-", ""), // 서버 포맷 yyyyMMdd
          bio,
          tags: tags
            .split(",")
            .map((tag) => tag.trim())
            .filter(Boolean),
          prefGender,
          prefAgeMin,
          prefAgeMax,
          prefDistanceKm,
        },
      });

      setStatus("현재 위치 확인 중…");
      const coords = await currentPosition();

      // 위치를 올려야 geo:users 에 들어가고, 그래야 추천 피드의 기준점이 생긴다.
      setStatus("위치 등록 중…");
      await api<void>(`/api/profiles/${profileId}/location`, {
        method: "PUT",
        body: coords,
      });

      router.replace("/feed");
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "프로필 생성에 실패했습니다");
      setStatus(null);
      setBusy(false);
    }
  };

  if (checking) {
    return <Centered>확인 중…</Centered>;
  }

  return (
    <div className="mx-auto w-full max-w-md px-4 py-10">
      <h1 className="text-2xl font-bold">프로필 만들기</h1>
      <p className="mt-1 text-sm opacity-60">
        추천을 받으려면 프로필과 위치가 필요합니다.
      </p>

      <form onSubmit={submit} className="mt-6 space-y-4">
        <Field label="내 성별">
          <Segmented value={gender} onChange={setGender} />
        </Field>

        <Field label="생일">
          <input
            required
            type="date"
            value={birthday}
            onChange={(e) => setBirthday(e.target.value)}
            className={inputClass}
          />
        </Field>

        <Field label="소개">
          <textarea
            required
            rows={2}
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            className={inputClass}
          />
        </Field>

        <Field label="관심사 (쉼표로 구분)">
          <input
            value={tags}
            onChange={(e) => setTags(e.target.value)}
            className={inputClass}
            placeholder="러닝, 여행, 커피"
          />
          <span className="mt-1 block text-xs opacity-50">
            상대와 겹치는 관심사가 많을수록 추천 순위가 올라갑니다.
          </span>
        </Field>

        <hr className="border-black/10 dark:border-white/10" />

        <Field label="찾는 성별">
          <Segmented value={prefGender} onChange={setPrefGender} />
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label="최소 나이">
            <input
              required
              type="number"
              min={18}
              max={99}
              value={prefAgeMin}
              onChange={(e) => setPrefAgeMin(Number(e.target.value))}
              className={inputClass}
            />
          </Field>
          <Field label="최대 나이">
            <input
              required
              type="number"
              min={18}
              max={99}
              value={prefAgeMax}
              onChange={(e) => setPrefAgeMax(Number(e.target.value))}
              className={inputClass}
            />
          </Field>
        </div>

        <Field label={`반경 ${prefDistanceKm}km`}>
          {/* 서버 검증이 1~10km 라 슬라이더 상한도 10 */}
          <input
            type="range"
            min={1}
            max={10}
            value={prefDistanceKm}
            onChange={(e) => setPrefDistanceKm(Number(e.target.value))}
            className="w-full accent-rose-500"
          />
        </Field>

        {error && (
          <p className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-950 dark:text-red-300">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={busy}
          className="w-full rounded-xl bg-rose-500 py-2.5 text-sm font-semibold text-white transition hover:bg-rose-600 disabled:opacity-50"
        >
          {busy ? (status ?? "처리 중…") : "시작하기"}
        </button>
      </form>
    </div>
  );
}

/**
 * 브라우저 위치. 권한 거부/실패해도 온보딩을 막지 않는다 — 기본 좌표로 넘어간다.
 * (localhost 는 보안 컨텍스트로 취급되므로 http 여도 geolocation 이 동작한다.)
 */
function currentPosition(): Promise<{ lat: number; lng: number }> {
  return new Promise((resolve) => {
    if (!navigator.geolocation) return resolve(FALLBACK);
    navigator.geolocation.getCurrentPosition(
      (position) =>
        resolve({
          lat: position.coords.latitude,
          lng: position.coords.longitude,
        }),
      () => resolve(FALLBACK),
      { timeout: 5000 },
    );
  });
}

const inputClass =
  "w-full rounded-lg border border-black/10 bg-white px-3 py-2 text-sm outline-none focus:border-rose-400 dark:border-white/15 dark:bg-neutral-800";

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-medium opacity-70">{label}</span>
      {children}
    </label>
  );
}

function Segmented({
  value,
  onChange,
}: {
  value: Gender;
  onChange: (next: Gender) => void;
}) {
  return (
    <div className="grid grid-cols-2 gap-1 rounded-lg bg-black/5 p-1 text-sm dark:bg-white/10">
      {(["MALE", "FEMALE"] as Gender[]).map((option) => (
        <button
          key={option}
          type="button"
          onClick={() => onChange(option)}
          className={[
            "rounded-md py-1.5 transition",
            value === option ? "bg-white shadow-sm dark:bg-neutral-800" : "opacity-60",
          ].join(" ")}
        >
          {option === "MALE" ? "남성" : "여성"}
        </button>
      ))}
    </div>
  );
}

function Centered({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center text-sm opacity-60">
      {children}
    </div>
  );
}
