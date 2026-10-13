import { useEffect, useRef, useState } from 'react';
import { authClient } from '../../auth';

type Props = {
  src: string;
  alt: string;
  loading?: 'eager' | 'lazy';
  className?: string;
  onError?: () => void;
};

/** Loads same-origin protected images with the in-memory OIDC bearer token. */
export function AuthenticatedImage({ src, alt, loading, className, onError }: Props) {
  const [objectUrl, setObjectUrl] = useState<string>();
  const onErrorRef = useRef(onError);
  onErrorRef.current = onError;
  if (src.startsWith('blob:') || src.startsWith('data:')) {
    return <img src={src} alt={alt} loading={loading} className={className} onError={onError} />;
  }
  useEffect(() => {
    let active = true;
    let created: string | undefined;
    void authClient.fetch(src, { cache: 'no-store' }).then(async response => {
      if (!response.ok) throw new Error(`thumbnail request failed (${response.status})`);
      created = URL.createObjectURL(await response.blob());
      if (active) setObjectUrl(created);
    }).catch(() => { if (active) onErrorRef.current?.(); });
    return () => {
      active = false;
      if (created) URL.revokeObjectURL(created);
    };
  }, [src]);
  return objectUrl ? <img src={objectUrl} alt={alt} loading={loading} className={className} onError={onError} /> : null;
}
